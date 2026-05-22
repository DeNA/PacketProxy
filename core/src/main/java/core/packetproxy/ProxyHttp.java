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

import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import packetproxy.Simplex.SimplexEventAdapter;
import packetproxy.common.Endpoint;
import packetproxy.common.EndpointFactory;
import packetproxy.common.SSLSocketEndpoint;
import packetproxy.common.SocketEndpoint;
import packetproxy.http.Http;
import packetproxy.http.Https;
import packetproxy.model.ListenPort;
import packetproxy.model.SSLPassThroughs;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class ProxyHttp extends Proxy {
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
				final Socket client = listen_socket.accept();
				clients.add(client);
				log("accept");

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
							Http http = Http.create(data);
							// Logging.log(String.format("%s: %s:%s", http.getMethod(),
							// http.getServerName(), http.getServerPort()));

							if (http.getMethod().equals("CONNECT")) {

								// HTTP2対応の都合上、ALPNを早期に確定する必要がある。
								// そのため、早めに「connection established」を返すことで、早めにSSLハンドシェイクを実施できるよう準備する。
								client_loopback
										.sendWithoutRecording("HTTP/1.0 200 Connection Established\r\n\r\n".getBytes());

								String serverName = http.getServerName();
								if (serverName.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {

									serverName = Https.getCommonName(http.getServerAddr());
									log("Overwrite CN: %s --> %s", http.getServerName(), serverName);
								}

								if (SSLPassThroughs.getInstance().includes(serverName, listen_info.getPort())) {

									SocketEndpoint server_e = new SocketEndpoint(http.getServerAddr());
									SocketEndpoint client_e = new SocketEndpoint(client);
									DuplexAsync d = new DuplexAsync(client_e, server_e);
									d.start();
								} else {

									SSLSocketEndpoint clientE;
									SSLSocketEndpoint serverE;
									if (listen_info.getServer() != null) { // upstream proxyに接続する時

										SSLSocketEndpoint[] es = EndpointFactory.createBothSideSSLEndpoints(client,
												null, http.getServerAddr(), listen_info.getServer().getAddress(),
												http.getServerName(), listen_info.getCA().get());
										clientE = es[0];
										serverE = es[1];
									} else { // 直接サーバに接続する時

										SSLSocketEndpoint[] es = EndpointFactory.createBothSideSSLEndpoints(client,
												null, http.getServerAddr(), null, http.getServerName(),
												listen_info.getCA().get());
										clientE = es[0];
										serverE = es[1];
									}
									String ALPN = clientE.getApplicationProtocol();
									if (ALPN == null || ALPN.length() == 0) {

										/* The client does not support ALPN. It seems to be an old HTTP client */
										ALPN = "http/1.1";
									}
									Server serverSetting = Servers.getInstance().queryByAddress(http.getServerAddr());
									String encoderName = (serverSetting != null) ? serverSetting.getEncoder() : "HTTP";
									DuplexAsync d = DuplexFactory.createDuplexAsync(clientE, serverE, encoderName,
											ALPN);
									d.start();
								}

								client_loopback.finishWithoutClose();
							} else if (http.isProxy()) {

								SocketEndpoint client_e = new SocketEndpoint(client);
								Server next = listen_info.getServer();
								Endpoint server_e = null;

								if (next != null) { // connect to upstream proxy

									server_e = new SocketEndpoint(next.getAddress());
								} else {

									http.disableProxyFormatUrl(); // direct connect!
									Server s = Servers.getInstance().queryByAddress(http.getServerAddr());
									if (s != null) {

										server_e = EndpointFactory.createFromServer(s);
									} else {

										server_e = new SocketEndpoint(http.getServerAddr());
									}
								}

								boolean flag_keepalive = false;
								if (http.getHeader().getAll("Connection").contains("keep-alive")) {

									flag_keepalive = true;
								}
								http.getHeader().update("Connection", "close");
								http.getHeader().removeAll("Proxy-Connection");

								Http response = Http.create(createConnection(client_e, server_e, http.toByteArray()));

								if (response.getHeader().getAll("Connection").contains("keep-alive")
										&& flag_keepalive == true) {

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
				errWithStackTrace(e);
			}
		}
		for (Socket sc : clients) {
			try {
				sc.close();
			} catch (Exception e) {
				errWithStackTrace(e);
			}
		}
	}

	public void close() throws Exception {
		listen_socket.close();
	}

	private byte[] createConnection(Endpoint client, Endpoint server, byte[] input_data) throws Exception {
		Server s = Servers.getInstance().queryByAddress(server.getAddress());
		DuplexSync duplex = (s != null)
				? DuplexFactory.createDuplexSync(client, server, s.getEncoder(), "http/1.1")
				: DuplexFactory.createDuplexSync(client, server, "HTTP", "http/1.1");
		duplex.send(input_data);
		byte[] output_data = duplex.receive();
		duplex.close();
		return output_data;
	}
}
