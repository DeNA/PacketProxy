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

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import packetproxy.common.EndpointFactory;
import packetproxy.common.SSLSocketEndpoint;
import packetproxy.common.SocketEndpoint;
import packetproxy.encode.EncodeHTTPBase;
import packetproxy.encode.Encoder;
import packetproxy.model.ListenPort;
import packetproxy.model.Server;
import packetproxy.model.SSLPassThroughs;
import packetproxy.util.PacketProxyUtility;

public class ProxySSLForward extends Proxy {
	private ListenPort listen_info;
	private ServerSocket listen_socket;

	public ProxySSLForward(ServerSocket listen_socket, ListenPort listen_info) {
		this.listen_socket = listen_socket;
		this.listen_info = listen_info;
	}

	@Override
	public void run() {
		List<Socket> clients = new ArrayList<Socket>();
		while (!listen_socket.isClosed()) {
			try {
				Socket client = listen_socket.accept();
				clients.add(client);
				PacketProxyUtility.getInstance().packetProxyLog("[SSLForward] accept");
				checkSSLForward(client);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		for (Socket sc : clients) {
			try {
				sc.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void checkSSLForward(Socket client) throws Exception {
		Server server = listen_info.getServer();
		InetSocketAddress serverAddr = server.getAddress();
		if (SSLPassThroughs.getInstance().includes(server.getIp(), listen_info.getPort())) {
			SocketEndpoint server_e = new SocketEndpoint(serverAddr);
			SocketEndpoint client_e = new SocketEndpoint(client);
			DuplexAsync duplex = new DuplexAsync(client_e, server_e);
			duplex.start();
		} else {
			SSLSocketEndpoint[] eps = EndpointFactory.createBothSideSSLEndpoints(client, null, serverAddr, null,
					listen_info.getServer().getIp(), listen_info.getCA().get());
			createConnection(eps[0], eps[1], listen_info.getServer());
		}
	}

	public void createConnection(SSLSocketEndpoint client_e, SSLSocketEndpoint server_e, Server server)
			throws Exception {
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

	public void close() throws Exception {
		listen_socket.close();
	}
}
