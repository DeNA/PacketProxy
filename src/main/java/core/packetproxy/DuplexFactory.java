/*
 * Copyright 2019 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;

import packetproxy.common.CryptUtils;
import packetproxy.common.Endpoint;
import packetproxy.common.EndpointFactory;
import packetproxy.common.SSLSocketEndpoint;
import packetproxy.common.UniqueID;
import packetproxy.controller.InterceptController;
import packetproxy.encode.Encoder;
import packetproxy.http.Http;
import packetproxy.http.HttpsProxySocketEndpoint;
import packetproxy.model.Modifications;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class DuplexFactory {

	// 1MB以上のパケットは最後のタイミングだけHistoryに記録する、それ未満はパケットが更新されるたびにHistoryを更新する
	static final int SKIP_LENGTH = 1 * 1024 * 1024;

	static public DuplexSync createDuplexSync(Endpoint client_endpoint, Endpoint server_endpoint, String encoder_name, String ALPN) throws Exception
	{
		DuplexSync duplex = new DuplexSync(server_endpoint);
		prepareDuplex(duplex, client_endpoint, server_endpoint, encoder_name, ALPN);
		return duplex;
	}

	static public DuplexAsync createDuplexAsync(Endpoint client_endpoint, Endpoint server_endpoint, String encoder_name) throws Exception {
		DuplexAsync duplex = new DuplexAsync(client_endpoint, server_endpoint);
		prepareDuplex(duplex, client_endpoint, server_endpoint, encoder_name, null);
		return duplex;
	}
	
	static public DuplexAsync createDuplexAsync(Endpoint client_endpoint, Endpoint server_endpoint, String encoder_name, String ALPN) throws Exception {
		DuplexAsync duplex = new DuplexAsync(client_endpoint, server_endpoint);
		prepareDuplex(duplex, client_endpoint, server_endpoint, encoder_name, ALPN);
		return duplex;
	}
	
	static private void prepareDuplex(final Duplex duplex, Endpoint client_endpoint, Endpoint server_endpoint, final String encoder_name, String ALPN) throws Exception
	{
		final InetSocketAddress client_addr = client_endpoint.getAddress();
		final InetSocketAddress server_addr = server_endpoint.getAddress();
		final boolean use_ssl;

		if (server_endpoint instanceof SSLSocketEndpoint) {
			use_ssl = true;
		} else if (server_endpoint instanceof HttpsProxySocketEndpoint) {
			use_ssl = true;
		} else {
			use_ssl = false;
		}
		
		duplex.addDuplexEventListener(new Duplex.DuplexEventListener() {
			private Packets packets = Packets.getInstance();
			private Encoder encoder = EncoderManager.getInstance().createInstance(encoder_name, ALPN);
			private Modifications mods = Modifications.getInstance();
			private Packet client_packet;
			private Packet server_packet;
			@Override
			public int onClientPacketReceived(byte[] data) throws Exception {
				return encoder.checkRequestDelimiter(data);
			}
			@Override
			public int onServerPacketReceived(byte[] data) throws Exception {
				return encoder.checkResponseDelimiter(data);
			}
			@Override
			public byte[] onClientChunkReceived(byte[] data) throws Exception {
				client_packet = new Packet(0, client_addr, server_addr, server_endpoint.getName(), use_ssl, encoder_name, ALPN, Packet.Direction.CLIENT, duplex.hashCode(), UniqueID.getInstance().createId());
				packets.update(client_packet);
				client_packet.setReceivedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(client_packet); }
				byte[] decoded_data = encoder.decodeClientRequest(client_packet);
				client_packet.setDecodedData(decoded_data);
				encoder.setGroupId(client_packet); /* 実行するのはsetDecodedDataのあと */
				if (data.length < SKIP_LENGTH) { packets.update(client_packet); }

				Server server = Servers.getInstance().queryByAddress(server_addr);
				decoded_data = mods.replaceOnRequest(decoded_data, server, client_packet);

				byte[] decoded_hash = CryptUtils.sha1(decoded_data);
				byte[] intercepted_data = InterceptController.getInstance().received(decoded_data, server, client_packet);
				byte[] intercepted_hash = CryptUtils.sha1(intercepted_data);
				client_packet.setModifiedData(intercepted_data);
				if (intercepted_data.length > 0 && !Arrays.equals(decoded_hash,  intercepted_hash)) { client_packet.setModified(); }
				if (data.length < SKIP_LENGTH) { packets.update(client_packet); }
				if (intercepted_data.length == 0) { /* drop */
					client_packet.setModified();
					packets.update(client_packet);
					return new byte[]{};
				}
				return intercepted_data;
			}
			@Override
			public byte[] onServerChunkReceived(byte[] data) throws Exception {
				long group_id = 0;
				if (client_packet != null) {
					group_id = client_packet.getGroup();
				} else {
					// サーバから先にレスポンスがあった場合
					group_id = UniqueID.getInstance().createId();
				}
				server_packet = new Packet(0, client_addr, server_addr, server_endpoint.getName(), use_ssl, encoder_name, ALPN, Packet.Direction.SERVER, duplex.hashCode(), group_id);
				packets.update(server_packet);
				server_packet.setReceivedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(server_packet); }
				byte[] decoded_data = encoder.decodeServerResponse(client_packet, server_packet);
				server_packet.setDecodedData(decoded_data);
				encoder.setGroupId(server_packet); /* 実行するのはsetDecodedDataのあと */
				server_packet.setContentType(encoder.getContentType(client_packet, server_packet));
				if (data.length < SKIP_LENGTH) { packets.update(server_packet); }
				if (!server_packet.getContentType().equals("")) {
					client_packet.setContentType(server_packet.getContentType());
					packets.update(client_packet);
				}

				Server server = Servers.getInstance().queryByAddress(server_addr);
				decoded_data = mods.replaceOnResponse(decoded_data, server, server_packet);

				byte[] decoded_hash = CryptUtils.sha1(decoded_data);
				byte[] intercepted_data = InterceptController.getInstance().received(decoded_data, server, client_packet, server_packet);
				byte[] intercepted_hash = CryptUtils.sha1(intercepted_data);
				server_packet.setModifiedData(intercepted_data);
				if (intercepted_data.length > 0 && !Arrays.equals(decoded_hash,  intercepted_hash)) { server_packet.setModified(); }
				if (data.length < SKIP_LENGTH) { packets.update(server_packet); }
				if (intercepted_data.length == 0) { /* drop */
					server_packet.setModified();
					packets.update(server_packet);
					return new byte[]{};
				}
				return intercepted_data;
			}
			@Override
			public byte[] onClientChunkSend(byte[] data) throws Exception {
				byte[] encoded_data = encoder.encodeClientRequest(client_packet);
				client_packet.setSentData(encoded_data);
				packets.update(client_packet);
				return encoded_data;
			}
			@Override
			public byte[] onServerChunkSend(byte[] data) throws Exception {
				byte[] encoded_data = encoder.encodeServerResponse(client_packet, server_packet);

				// 画像データの場合には、ディスクスペース節約のためにDBに保存しない
				if (server_packet.getContentType().startsWith("image")) {
					Http http = new Http(server_packet.getDecodedData());
					http.setBody("[Info] body data were deleted by PacketProxy to save space of disc.".getBytes());
					server_packet.setReceivedData(http.toByteArray());
					server_packet.setDecodedData(http.toByteArray());
					server_packet.setModifiedData(http.toByteArray());
					server_packet.setSentData(http.toByteArray());
				} else {
					server_packet.setSentData(encoded_data);
				}

				packets.update(server_packet);
				return encoded_data;
			}
			@Override
			public byte[] onClientChunkSendForced(byte[] data) throws Exception {
				Packet client_packet = new Packet(0, client_addr, server_addr, server_endpoint.getName(), use_ssl, encoder_name, ALPN, Packet.Direction.CLIENT, duplex.hashCode(), UniqueID.getInstance().createId());
				packets.update(client_packet);
				client_packet.setModified();
				client_packet.setDecodedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(client_packet); }
				client_packet.setModifiedData(data);
				client_packet.setModifiedData(encoder.procBeforeResendClientRequest(client_packet));
				if (data.length < SKIP_LENGTH) { packets.update(client_packet); }
				byte[] encoded_data = encoder.encodeClientRequest(client_packet);
				client_packet.setSentData(encoded_data);
				packets.update(client_packet);
				return encoded_data;
			}
			@Override
			public byte[] onServerChunkSendForced(byte[] data) throws Exception {
				long group_id = 0;
				if (client_packet != null) {
					group_id = client_packet.getGroup();
				} else {
					// サーバから先にレスポンスがあった場合
					group_id = UniqueID.getInstance().createId();
				}
				Packet server_packet = new Packet(0, client_addr, server_addr, server_endpoint.getName(), use_ssl, encoder_name, ALPN, Packet.Direction.SERVER, duplex.hashCode(), group_id);
				packets.update(server_packet);
				server_packet.setDecodedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(server_packet); }
				server_packet.setModifiedData(data);
				server_packet.setModifiedData(encoder.procBeforeResendServerResponse(server_packet));
				if (data.length < SKIP_LENGTH) { packets.update(server_packet); }
				byte[] encoded_data = encoder.encodeServerResponse(client_packet, server_packet);
				server_packet.setSentData(encoded_data);
				packets.update(server_packet);
				return encoded_data;
			}
			@Override
			public void onClientChunkArrived(byte[] data) throws Exception {
				encoder.clientRequestArrived(data);
			}
			@Override
			public void onServerChunkArrived(byte[] data) throws Exception {
				encoder.serverResponseArrived(data);
			}
			@Override
			public byte[] onClientChunkPassThrough() throws Exception {
				return encoder.passThroughClientRequest();
			}
			@Override
			public byte[] onServerChunkPassThrough() throws Exception {
				return encoder.passThroughServerResponse();
			}
			@Override
			public byte[] onClientChunkAvailable() throws Exception {
				return encoder.clientRequestAvailable();
			}
			@Override
			public byte[] onServerChunkAvailable() throws Exception {
				return encoder.serverResponseAvailable();
			}
			@Override
			public void onClientChunkFlowControl(byte[] data) throws Exception {
				encoder.putToClientFlowControlledQueue(data);
			}
			@Override
			public void onServerChunkFlowControl(byte[] data) throws Exception {
				encoder.putToServerFlowControlledQueue(data);
			}
			@Override
			public void closeClientChunkFlowControl() throws Exception {
				encoder.closeClientFlowControlledQueue();
			}
			@Override
			public void closeServerChunkFlowControl() throws Exception {
				encoder.closeServerFlowControlledQueue();
			}
			@Override
			public InputStream getClientChunkFlowControlSink() throws Exception {
				return encoder.getClientFlowControlledInputStream();
			}
			@Override
			public InputStream getServerChunkFlowControlSink() throws Exception {
				return encoder.getServerFlowControlledInputStream();
			}
		});
	}
	static  public DuplexSync createDuplexSyncFromOneShotPacket(final OneShotPacket oneshot) throws Exception {
		DuplexSync duplex = new DuplexSync(EndpointFactory.createFromOneShotPacket(oneshot));
		duplex.addDuplexEventListener(new Duplex.DuplexEventListener() {
			private Packets packets = Packets.getInstance();
			private Encoder encoder = EncoderManager.getInstance().createInstance(oneshot.getEncoder(), oneshot.getAlpn());
			private Packet client_packet;
			private Packet server_packet;
			@Override
			public int onClientPacketReceived(byte[] data) throws Exception {
				//return encoder.checkDelimitor(data);
				return data.length;
			}
			@Override
			public int onServerPacketReceived(byte[] data) throws Exception {
				return encoder.checkResponseDelimiter(data);
			}
			@Override
			public byte[] onClientChunkReceived(byte[] data) throws Exception {
				/* do nothing so far */
				return data;
			}
			@Override
			public byte[] onServerChunkReceived(byte[] data) throws Exception {
				long group_id = 0;
				if (client_packet != null) {
					group_id = client_packet.getGroup();
				} else {
					// サーバから先にレスポンスがあった場合
					group_id = UniqueID.getInstance().createId();
				}
				server_packet = new Packet(0, oneshot.getClient(), oneshot.getServer(), oneshot.getServerName(), oneshot.getUseSSL(), oneshot.getEncoder(), oneshot.getAlpn(), Packet.Direction.SERVER, duplex.hashCode(), group_id);
				packets.update(server_packet);
				server_packet.setReceivedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(server_packet); }
				byte[] decoded_data = encoder.decodeServerResponse(client_packet, server_packet);
				server_packet.setDecodedData(decoded_data);
				server_packet.setContentType(encoder.getContentType(client_packet, server_packet));
				if (!server_packet.getContentType().equals("")) {
					client_packet.setContentType(server_packet.getContentType());
					packets.update(client_packet);
				}
				server_packet.setModifiedData(decoded_data);
				packets.update(server_packet);
				if (decoded_data.length == 0) { /* drop */
					server_packet.setModified();
					packets.update(server_packet);
					return new byte[]{};
				}
				return decoded_data;
			}
			@Override
			public byte[] onClientChunkSend(byte[] data) throws Exception {
				client_packet = new Packet(0, oneshot.getClient(), oneshot.getServer(), oneshot.getServerName(), oneshot.getUseSSL(), oneshot.getEncoder(), oneshot.getAlpn(), Packet.Direction.CLIENT, duplex.hashCode(), UniqueID.getInstance().createId());
				packets.update(client_packet);
				client_packet.setModified();
				client_packet.setDecodedData(data);
				client_packet.setModifiedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(client_packet); }
				byte[] encoded_data = encoder.encodeClientRequest(client_packet);
				client_packet.setSentData(encoded_data);
				packets.update(client_packet);
				return encoded_data;
			}
			@Override
			public byte[] onServerChunkSend(byte[] data) throws Exception {
				return data;
			}
			@Override
			public byte[] onClientChunkSendForced(byte[] data) throws Exception {
				return null;
			}
			@Override
			public byte[] onServerChunkSendForced(byte[] data) throws Exception {
				return null;
			}
			@Override
			public void onClientChunkArrived(byte[] data) throws Exception {
				encoder.clientRequestArrived(data);
			}
			@Override
			public void onServerChunkArrived(byte[] data) throws Exception {
				encoder.serverResponseArrived(data);
			}
			@Override
			public byte[] onClientChunkPassThrough() throws Exception {
				return encoder.passThroughClientRequest();
			}
			@Override
			public byte[] onServerChunkPassThrough() throws Exception {
				return encoder.passThroughServerResponse();
			}
			@Override
			public byte[] onClientChunkAvailable() throws Exception {
				return encoder.clientRequestAvailable();
			}
			@Override
			public byte[] onServerChunkAvailable() throws Exception {
				return encoder.serverResponseAvailable();
			}
			@Override
			public void onClientChunkFlowControl(byte[] data) throws Exception {
				encoder.putToClientFlowControlledQueue(data);
			}
			@Override
			public void onServerChunkFlowControl(byte[] data) throws Exception {
				encoder.putToServerFlowControlledQueue(data);
			}
			@Override
			public void closeClientChunkFlowControl() throws Exception {
				encoder.closeClientFlowControlledQueue();
			}
			@Override
			public void closeServerChunkFlowControl() throws Exception {
				encoder.closeServerFlowControlledQueue();
			}
			@Override
			public InputStream getClientChunkFlowControlSink() throws Exception {
				return encoder.getClientFlowControlledInputStream();
			}
			@Override
			public InputStream getServerChunkFlowControlSink() throws Exception {
				return encoder.getServerFlowControlledInputStream();
			}
		});
		return duplex;
	}

	// original_duplexと接続を共有しているが、イベントリスナーは再送用のものに差し替えたDuplexを返す
	public static Duplex createDuplexFromOriginalDuplex(Duplex original_duplex, OneShotPacket oneshot) throws Exception {
		Duplex duplex = original_duplex.createSameConnectionDuplex();
		duplex.addDuplexEventListener(new Duplex.DuplexEventListener() {
			private Packets packets = Packets.getInstance();
			private Encoder encoder = EncoderManager.getInstance().createInstance(oneshot.getEncoder(), oneshot.getAlpn());
			private Packet client_packet;
			private Packet server_packet;
			@Override
			public int onClientPacketReceived(byte[] data) throws Exception {
				//return encoder.checkDelimitor(data);
				return data.length;
			}
			@Override
			public int onServerPacketReceived(byte[] data) throws Exception {
				return encoder.checkResponseDelimiter(data);
			}
			@Override
			public byte[] onClientChunkReceived(byte[] data) throws Exception {
				/* do nothing so far */
				return data;
			}
			@Override
			public byte[] onServerChunkReceived(byte[] data) throws Exception {
				long group_id = 0;
				if (client_packet != null) {
					group_id = client_packet.getGroup();
				} else {
					// サーバから先にレスポンスがあった場合
					group_id = UniqueID.getInstance().createId();
				}
				server_packet = new Packet(0, oneshot.getClient(), oneshot.getServer(), oneshot.getServerName(), oneshot.getUseSSL(), oneshot.getEncoder(), oneshot.getAlpn(), Packet.Direction.SERVER, original_duplex.hashCode(), group_id);
				packets.update(server_packet);
				server_packet.setReceivedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(server_packet); }
				byte[] decoded_data = encoder.decodeServerResponse(client_packet, server_packet);
				server_packet.setDecodedData(decoded_data);
				server_packet.setContentType(encoder.getContentType(client_packet, server_packet));
				if (!server_packet.getContentType().equals("")) {
					client_packet.setContentType(server_packet.getContentType());
					packets.update(client_packet);
				}
				server_packet.setModifiedData(decoded_data);
				packets.update(server_packet);
				if (decoded_data.length == 0) { /* drop */
					server_packet.setModified();
					packets.update(server_packet);
					return new byte[]{};
				}
				return decoded_data;
			}
			@Override
			public byte[] onClientChunkSend(byte[] data) throws Exception {
				client_packet = new Packet(0, oneshot.getClient(), oneshot.getServer(), oneshot.getServerName(), oneshot.getUseSSL(), oneshot.getEncoder(), oneshot.getAlpn(), Packet.Direction.CLIENT, original_duplex.hashCode(), UniqueID.getInstance().createId());
				packets.update(client_packet);
				client_packet.setModified();
				client_packet.setDecodedData(data);
				client_packet.setModifiedData(data);
				if (data.length < SKIP_LENGTH) { packets.update(client_packet); }
				byte[] encoded_data = encoder.encodeClientRequest(client_packet);
				client_packet.setSentData(encoded_data);
				packets.update(client_packet);
				return encoded_data;
			}
			@Override
			public byte[] onServerChunkSend(byte[] data) throws Exception {
				return data;
			}
			@Override
			public byte[] onClientChunkSendForced(byte[] data) throws Exception {
				return null;
			}
			@Override
			public byte[] onServerChunkSendForced(byte[] data) throws Exception {
				return null;
			}
			@Override
			public void onClientChunkArrived(byte[] data) throws Exception {
				encoder.clientRequestArrived(data);
			}
			@Override
			public void onServerChunkArrived(byte[] data) throws Exception {
				encoder.serverResponseArrived(data);
			}
			@Override
			public byte[] onClientChunkPassThrough() throws Exception {
				return encoder.passThroughClientRequest();
			}
			@Override
			public byte[] onServerChunkPassThrough() throws Exception {
				return encoder.passThroughServerResponse();
			}
			@Override
			public byte[] onClientChunkAvailable() throws Exception {
				return encoder.clientRequestAvailable();
			}
			@Override
			public byte[] onServerChunkAvailable() throws Exception {
				return encoder.serverResponseAvailable();
			}
			@Override
			public void onClientChunkFlowControl(byte[] data) throws Exception {
				encoder.putToClientFlowControlledQueue(data);
			}
			@Override
			public void onServerChunkFlowControl(byte[] data) throws Exception {
				encoder.putToServerFlowControlledQueue(data);
			}
			@Override
			public void closeClientChunkFlowControl() throws Exception {
				encoder.closeClientFlowControlledQueue();
			}
			@Override
			public void closeServerChunkFlowControl() throws Exception {
				encoder.closeServerFlowControlledQueue();
			}
			@Override
			public InputStream getClientChunkFlowControlSink() throws Exception {
				return encoder.getClientFlowControlledInputStream();
			}
			@Override
			public InputStream getServerChunkFlowControlSink() throws Exception {
				return encoder.getServerFlowControlledInputStream();
			}
		});
		return duplex;
	}
}
