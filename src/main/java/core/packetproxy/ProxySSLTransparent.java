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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SNIServerName;

import org.apache.commons.lang3.ArrayUtils;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

import packetproxy.common.EndpointFactory;
import packetproxy.common.I18nString;
import packetproxy.common.SocketEndpoint;
import packetproxy.common.SSLCapabilities;
import packetproxy.common.SSLExplorer;
import packetproxy.common.SSLSocketEndpoint;
import packetproxy.common.WrapEndpoint;
import packetproxy.encode.EncodeHTTPBase;
import packetproxy.encode.Encoder;
import packetproxy.model.ListenPort;
import packetproxy.model.Server;
import packetproxy.model.Servers;
import packetproxy.model.SSLPassThroughs;
import packetproxy.util.PacketProxyUtility;

public class ProxySSLTransparent extends Proxy
{
	private ListenPort listen_info;
	private ServerSocket listen_socket;

	public ProxySSLTransparent(ServerSocket listen_socket, ListenPort listen_info) throws Exception {
		this.listen_socket = listen_socket;
		this.listen_info = listen_info;
	}

	public void close() throws Exception {
		listen_socket.close();
	}

	@Override
	public void run() {
		List<Socket> clients = new ArrayList<Socket>();
		while (!listen_socket.isClosed()) {
			try {
				Socket client = listen_socket.accept();
				clients.add(client);
				PacketProxyUtility.getInstance().packetProxyLog("[ProxySSLTransparent]: accept");
				checkTransparentSSLProxy(client, listen_socket.getLocalPort());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		for(Socket sc : clients) {
			try {
				sc.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void checkTransparentSSLProxy(Socket client, int proxyPort) throws Exception {
		InputStream ins = client.getInputStream();

		byte[] buffer = new byte[0xFF];
		int position = 0;
		SSLCapabilities capabilities = null;

		// Read the header of TLS record
		while (position < SSLExplorer.RECORD_HEADER_SIZE) {
			int count = SSLExplorer.RECORD_HEADER_SIZE - position;
			int n = ins.read(buffer, position, count);
			if (n < 0) {
				throw new Exception("unexpected end of stream!");
			}
			position += n;
		}

		// Get the required size to explore the SSL capabilities
		int recordLength = SSLExplorer.getRequiredSize(buffer, 0, position);
		if (buffer.length < recordLength) {
			buffer = Arrays.copyOf(buffer, recordLength);
		}

		while (position < recordLength) {
			int count = recordLength - position;
			int n = ins.read(buffer, position, count);
			if (n < 0) {
				throw new Exception("unexpected end of stream!");
			}
			position += n;
		}

		// Explore
		capabilities = SSLExplorer.explore(buffer, 0, recordLength);
		if (capabilities == null) {
			throw new Exception("capabilities not found.");
		}

		List<SNIServerName> serverNames = capabilities.getServerNames();
		if (serverNames.isEmpty()) {
			ByteArrayInputStream bais = new ByteArrayInputStream(buffer, 0, position);
			/* 証明書のチェックルーチンは必ずはずしておいてください！ */
			SSLSocketEndpoint client_e = EndpointFactory.createClientEndpointFromSNIServerName(client, "packetproxy.com", listen_info.getCA().get(), bais);
			/* 少しだけ先読みし、Hostフィールドから次に接続するべきサーバー名を入手 */
			InputStream in = client_e.getInputStream();
			byte[] buff = new byte[2048];
			int length = in.read(buff);
			String str = new String(buff);
			Pattern pattern = Pattern.compile("Host: *([^\r\n]+)", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(str);
			String serverName = "";
			if (matcher.find()) {
				serverName = matcher.group(1);
				PacketProxyUtility.getInstance().packetProxyLog(String.format("[SSL-forward!] %s", serverName));
			} else {
				throw new Exception(I18nString.get("[Error] SNI header was not found in SSL packets."));
			}
			WrapEndpoint wep_e = new WrapEndpoint(client_e, ArrayUtils.subarray(buff, 0, length));
			InetSocketAddress serverAddr = new InetSocketAddress(serverName, proxyPort);
			// SNIヘッダが無い場合、SSLPassThroughは使えない
			Server server = Servers.getInstance().queryByHostNameAndPort(serverName, proxyPort);
			SSLSocketEndpoint server_e = new SSLSocketEndpoint(serverAddr, serverName, null);
			createConnection(wep_e, server_e, server);

		} else {
			for (SNIServerName serverE: serverNames) {
				String serverName = new String(serverE.getEncoded()); // 接続先サーバを取得
				PacketProxyUtility.getInstance().packetProxyLog(String.format("[SSL-forward! using SNI] %s", serverName));
				ByteArrayInputStream bais = new ByteArrayInputStream(buffer, 0, position);
				
				/* check server connection */
				InetSocketAddress serverAddr;
				try {
					serverAddr = new InetSocketAddress(serverName, proxyPort);
					Socket s = new Socket();
					s.connect(serverAddr, 500); /* timeout: 500ms */
					s.close();
				} catch (Exception e) {
					/* listenポート番号と同じポート番号へアクセスできないので443番にフォールバックする */
					serverAddr = new InetSocketAddress(serverName, 443);
					PacketProxyUtility.getInstance().packetProxyLog("[Follback port] " + proxyPort + " -> 443");
				}
				
				if (SSLPassThroughs.getInstance().includes(serverName, listen_info.getPort())) {
					SocketEndpoint server_e = new SocketEndpoint(serverAddr);
					SocketEndpoint client_e = new SocketEndpoint(client, bais);
					DuplexAsync duplex = new DuplexAsync(client_e, server_e);
					duplex.start();
				} else {
					Server server = Servers.getInstance().queryByHostNameAndPort(serverName, serverAddr.getPort());
					SSLSocketEndpoint[] eps = EndpointFactory.createBothSideSSLEndpoints(client, bais, serverAddr, null, serverName, listen_info.getCA().get());
					createConnection(eps[0], eps[1], server);
				}
			}
		}
	}

	public void createConnection(SSLSocketEndpoint client_e, SSLSocketEndpoint server_e, Server server) throws Exception {
		DuplexAsync duplex = null;
		String alpn = client_e.getApplicationProtocol();
		if (server == null) {
			if (alpn.equals("h2") || alpn.equals("http/1.1") || alpn.equals("http/1.0")) {
				duplex = DuplexFactory.createDuplexAsync(client_e, server_e, "HTTP", alpn);
			} else {
				duplex = DuplexFactory.createDuplexAsync(client_e, server_e, "Sample", alpn);
			}
		} else {
			if (alpn == null || alpn.length() == 0) {
				Encoder encoder = EncoderManager.getInstance().createInstance(server.getEncoder(), "");
				if (encoder instanceof EncodeHTTPBase) {
					/* The client does not support ALPN. It seems to be an old HTTP client */
					alpn = "http/1.1";
				}
			}
			duplex = DuplexFactory.createDuplexAsync(client_e, server_e, server.getEncoder(), alpn);
		}
		duplex.start();
		DuplexManager.getInstance().registerDuplex(duplex);
	}
}
