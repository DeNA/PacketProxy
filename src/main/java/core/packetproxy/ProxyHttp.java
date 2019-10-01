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

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import packetproxy.Simplex.SimplexEventAdapter;
import packetproxy.common.Endpoint;
import packetproxy.common.EndpointFactory;
import packetproxy.common.SocketEndpoint;
import packetproxy.http.Http;
import packetproxy.http.Https;
import packetproxy.http.HttpsProxySocketEndpoint;
import packetproxy.model.ListenPort;
import packetproxy.model.SSLPassThroughs;
import packetproxy.model.Server;
import packetproxy.model.Servers;
import packetproxy.util.PacketProxyUtility;

public class ProxyHttp extends Proxy
{
	private ListenPort listen_info;
	private ServerSocket listen_socket;

	public ProxyHttp(ServerSocket listen_socket, ListenPort listen_info) throws Exception {
		this.listen_socket = listen_socket;
		this.listen_info = listen_info;
	}

	@Override
	public void run() {
		List<Socket> clients = new ArrayList<Socket>();
		while (!listen_socket.isClosed()) {
			try {
				PacketProxyUtility util = PacketProxyUtility.getInstance();
				final Socket client = listen_socket.accept();
				clients.add(client);
				util.packetProxyLog("accept");

				final Simplex client_loopback = new Simplex(client.getInputStream(), client.getOutputStream());

				client_loopback.addSimplexEventListener(new SimplexEventAdapter() {
					@Override
					public int onPacketReceived(byte[] data) throws Exception {
						return Http.parseHttpDelimiter(data);
					}
					@Override
					public byte[] onChunkReceived(byte[] data) throws Exception {
						byte[] result = new byte[]{};
						synchronized (client_loopback) {

							Http http = new Http(data);
							//System.out.println(String.format("%s: %s:%s", http.getMethod(), http.getProxyHost(), http.getProxyPort()));

							if (http.getMethod().equals("CONNECT")) {
									
								String proxyHost = http.getProxyHost();
								if (proxyHost.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
									proxyHost = Https.getCommonName(http.getProxyAddr());
									util.packetProxyLog(String.format("Overwrite CN: %s --> %s", http.getProxyHost(), proxyHost));
								}

								if (SSLPassThroughs.getInstance().includes(proxyHost, listen_info.getPort())) {
									SocketEndpoint client_e = new SocketEndpoint(client);
									SocketEndpoint server_e = new SocketEndpoint(http.getProxyAddr());
									DuplexAsync d = new DuplexAsync(client_e, server_e);
									d.start();
								} else {
									Endpoint client_e = EndpointFactory.createClientEndpointFromHttp(client, http, listen_info.getCA().get()); // CAはPacketProxy側で発行するので、httpでも取得可能
									Endpoint server_e = (listen_info.getServer() != null) ?
										new HttpsProxySocketEndpoint(listen_info.getServer().getAddress(), http.getProxyAddr()) : // connect to upstream proxy TODO 非httpsの場合の対処
										EndpointFactory.createServerEndpointFromHttp(http);
									Server s = Servers.getInstance().queryByAddress(http.getProxyAddr());
									DuplexAsync d = (s != null) ?
										DuplexFactory.createDuplexAsync(client_e, server_e, s.getEncoder()) :
										DuplexFactory.createDuplexAsync(client_e, server_e, "HTTP");
									d.start();
								}

								result = "HTTP/1.0 200 Connection Established\r\n\r\n".getBytes();
								client_loopback.finishWithoutClose();

							} else if (http.isProxy()) {

								SocketEndpoint client_e = new SocketEndpoint(client);
								Server next = listen_info.getServer();
								Endpoint server_e = null;

								if (next != null) { // connect to upstream proxy
									server_e = new SocketEndpoint(next.getAddress());
								} else {
									http.disableProxyFormatUrl(); // direct connect!
									Server s = Servers.getInstance().queryByAddress(http.getProxyAddr());
									if (s != null) {
										server_e = EndpointFactory.createFromServer(s);
									} else {
										server_e = new SocketEndpoint(http.getProxyAddr());
									}
								}

								boolean flag_keepalive = false;
								if (http.getHeader().getAll("Connection").contains("keep-alive")) {
									flag_keepalive = true;
								}
								http.getHeader().update("Connection", "close");
								http.getHeader().removeAll("Proxy-Connection");

								Http response = new Http(createConnection(client_e, server_e, http.toByteArray()));

								if (response.getHeader().getAll("Connection").contains("keep-alive") && flag_keepalive == true) {
									response.getHeader().update("Connection", "keep-alive");
									response.getHeader().update("Proxy-Connection", "keep-alive");
								} else {
									response.getHeader().update("Connection", "close");
									response.getHeader().update("Proxy-Connection", "close");
									client_loopback.close();
								}
								result = response.toByteArray();
							}
						}
						return result;
					}
				});
				client_loopback.start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		for(Socket sc : clients){
			try {
				sc.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public void close() throws Exception {
		listen_socket.close();
	}

	private byte[] createConnection(Endpoint client, Endpoint server, byte[] input_data) throws Exception {
		Server s = Servers.getInstance().queryByAddress(server.getAddress());
		DuplexSync duplex = (s != null) ?
				DuplexFactory.createDuplexSync(client, server, s.getEncoder()) :
				DuplexFactory.createDuplexSync(client, server, "HTTP");
		duplex.send(input_data);
		byte[] output_data =  duplex.receive();
		duplex.close();
		return output_data;
	}
}
