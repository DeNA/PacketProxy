/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy.quic.service.connection;

import static packetproxy.util.Throwing.rethrow;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Getter;
import packetproxy.common.Endpoint;
import packetproxy.common.PipeEndpoint;
import packetproxy.quic.service.LossDetection;
import packetproxy.quic.service.Pto;
import packetproxy.quic.service.RttEstimator;
import packetproxy.quic.service.connection.helper.AwaitingPackets;
import packetproxy.quic.service.handshake.Handshake;
import packetproxy.quic.service.handshake.HandshakeState;
import packetproxy.quic.service.key.Keys;
import packetproxy.quic.service.packet.QuicPacketParser;
import packetproxy.quic.service.pnspace.PnSpace;
import packetproxy.quic.service.pnspace.PnSpaces;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.utils.Constants.PnSpaceType;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.packet.QuicPacket;
import packetproxy.quic.value.packet.longheader.pnspace.HandshakePacket;
import packetproxy.quic.value.packet.longheader.pnspace.InitialPacket;
import packetproxy.quic.value.packet.shortheader.ShortHeaderPacket;

@Getter
public abstract class Connection implements Endpoint {
	final ExecutorService executor = Executors.newFixedThreadPool(3);
	final QuicPacketParser clientPacketParser;
	final QuicPacketParser serverPacketParser;
	final AwaitingPackets<QuicPacket> awaitingSendPackets = new AwaitingPackets();
	final AwaitingPackets<DatagramPacket> awaitingReceivedPackets = new AwaitingPackets();
	final HandshakeState handshakeState = new HandshakeState();
	final Keys keys = new Keys();
	final Constants.Role role;
	final DatagramSocket socket;
	final Pto pto;
	final LossDetection lossDetection;
	final RttEstimator rttEstimator;
	final PnSpaces pnSpaces;
	final PipeEndpoint pipe;
	final InetSocketAddress peerAddr;
	ConnectionIdPair connIdPair;
	ConnectionId initialSecret;
	boolean serverAntiAmplifiedLimit = false;

	public Connection(Constants.Role role, ConnectionIdPair connIdPair, ConnectionId initialSecret,
			DatagramSocket socket, InetSocketAddress peerAddr) throws Exception {
		this.role = role;
		this.connIdPair = connIdPair;
		this.socket = socket;
		this.peerAddr = peerAddr;
		this.clientPacketParser = new QuicPacketParser(this, keys.getClientKeys());
		this.serverPacketParser = new QuicPacketParser(this, keys.getServerKeys());
		this.pto = new Pto(this);
		this.lossDetection = new LossDetection(this);
		this.rttEstimator = new RttEstimator(this);
		this.pnSpaces = new PnSpaces(this);
		this.pipe = new PipeEndpoint(peerAddr);
		this.connIdPair = connIdPair;
		this.initialSecret = initialSecret;
		this.keys.computeInitialKey(initialSecret);
	}

	protected void start() throws Exception {
		/* 送信パケットキュー -> 送信 */
		this.executor.submit(new Runnable() {
			@Override
			public void run() {
				while (true) {
					List<QuicPacket> packets = pnSpaces.pollSendPackets(); // Blocking here
					awaitingSendPackets.put(packets);
					awaitingSendPackets.forEachAndRemovedIfReturnTrue(packet -> {
						try {
							// if (role == Constants.Role.SERVER) {
							// PacketProxyUtility.getInstance().packetProxyLog("[QUIC] CLIENT<--- " +
							// packet);
							// } else {
							// PacketProxyUtility.getInstance().packetProxyLog("[QUIC] --->SERVER " +
							// packet);
							// }
							if (packet instanceof InitialPacket) {
								InitialPacket ip = (InitialPacket) packet;
								if (keys.discardedInitialKey()) {
									return true; /* Initialキーは破棄済みなのでInitialパケットは送信せずに破棄する */
								}
								byte[] udpData = (role == Constants.Role.CLIENT)
										? ip.getBytes(keys.getClientKeys().getInitialKey(),
												getPnSpace(ip.getPnSpaceType()).getAckFrameGenerator()
														.getSmallestValidPn())
										: ip.getBytes(keys.getServerKeys().getInitialKey(),
												getPnSpace(ip.getPnSpaceType()).getAckFrameGenerator()
														.getSmallestValidPn());
								sendUdpPacket(udpData);
								return true;
							}
							if (packet instanceof HandshakePacket) {
								HandshakePacket hp = (HandshakePacket) packet;
								if (keys.discardedHandshakeKey()) {
									return true; /* Handshakeキーは破棄済みなのでHandshakeパケットは送信せずに破棄する */
								}
								byte[] udpData = (role == Constants.Role.CLIENT)
										? hp.getBytes(keys.getClientKeys().getHandshakeKey(),
												getPnSpace(hp.getPnSpaceType()).getAckFrameGenerator()
														.getSmallestValidPn())
										: hp.getBytes(keys.getServerKeys().getHandshakeKey(),
												getPnSpace(hp.getPnSpaceType()).getAckFrameGenerator()
														.getSmallestValidPn());
								sendUdpPacket(udpData);
								return true;
							}
							if (packet instanceof ShortHeaderPacket && keys.hasApplicationKey()) {
								ShortHeaderPacket sp = (ShortHeaderPacket) packet;
								if (role == Constants.Role.CLIENT && getHandshakeState().isNotConfirmed()) {
									return false; /* Handshakeが終わってからShortHeaderPacketを送信しないとサーバー側でエラーになってしまうので、後ほど送信 */
								}
								byte[] udpData = (role == Constants.Role.CLIENT)
										? sp.getBytes(keys.getClientKeys().getApplicationKey(),
												getPnSpace(sp.getPnSpaceType()).getAckFrameGenerator()
														.getSmallestValidPn())
										: sp.getBytes(keys.getServerKeys().getApplicationKey(),
												getPnSpace(sp.getPnSpaceType()).getAckFrameGenerator()
														.getSmallestValidPn());
								sendUdpPacket(udpData);
								return true;
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
						return false;
					});
				}
			}
		});

		/* エンコーダー -> 送信パケットキュー */
		this.executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					InputStream in = pipe.getRawEndpoint().getInputStream();

					/*
					 * ハンドシェイクが完了するまで待ってからエンコーダーからの入力をパケット化する
					 * ハンドシェイクが完了してApplicationKeyを得ていないとパケット化できないため
					 */
					if (role == Constants.Role.CLIENT) {
						while (true) {
							if (handshakeState.isConfirmed()) {
								break;
							}
							Thread.sleep(100);
						}
					}

					byte[] readChunk = new byte[4096];
					ByteArrayOutputStream readQueue = new ByteArrayOutputStream();
					int length;
					while ((length = in.read(readChunk)) > 0) {
						readQueue.write(readChunk, 0, length);
						ByteBuffer buffer = ByteBuffer.wrap(readQueue.toByteArray());
						QuicMessages.parse(buffer).forEach(rethrow(msg -> {
							getPnSpace(Constants.PnSpaceType.PnSpaceApplicationData).addSendQuicMessage(msg);
						}));
						readQueue.reset();
						if (buffer.hasRemaining()) {
							readQueue.write(SimpleBytes.parse(buffer, buffer.remaining()).getBytes());
						}
					}
				} catch (InterruptedIOException e) {
					/* exception simply ignored */
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void sendUdpPacket(byte[] data) throws Exception {
		DatagramPacket udpPacket = new DatagramPacket(data, 0, data.length, this.peerAddr);
		socket.send(udpPacket);
	}

	public void updateDestConnId(ConnectionId destConnId) {
		this.connIdPair = ConnectionIdPair.of(this.connIdPair.getSrcConnId(), destConnId);
	}

	public PnSpace getPnSpace(PnSpaceType pnSpaceType) {
		return this.pnSpaces.getPnSpace(pnSpaceType);
	}

	public void close() {
		try {
			this.pipe.getRawEndpoint().getInputStream().close();
			this.pipe.getRawEndpoint().getOutputStream().close();
			this.executor.shutdownNow();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public InputStream getInputStream() throws Exception {
		return this.pipe.getProxyRawEndpoint().getInputStream();
	}

	@Override
	public OutputStream getOutputStream() throws Exception {
		return this.pipe.getProxyRawEndpoint().getOutputStream();
	}

	@Override
	public InetSocketAddress getAddress() {
		return this.peerAddr;
	}

	@Override
	public int getLocalPort() {
		return 0;
	}

	@Override
	public String getName() {
		return "QUIC Endpoint";
	}

	public boolean peerAwaitingAddressValidation() {
		return !this.peerCompletedAddressValidation();
	}

	public abstract boolean peerCompletedAddressValidation();

	public abstract Handshake getHandshake();

}
