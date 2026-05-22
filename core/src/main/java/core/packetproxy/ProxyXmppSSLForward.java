/*
 * Copyright 2019,2022 DeNA Co., Ltd.
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

import static packetproxy.http.Https.createSSLContext;
import static packetproxy.http.Https.createSSLSocketFactory;
import static packetproxy.util.Logging.err;
import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Endpoint;
import packetproxy.common.SocketEndpoint;
import packetproxy.model.ListenPort;

public class ProxyXmppSSLForward extends Proxy {

	private ListenPort listen_info;
	private ServerSocket listen_socket;
	private boolean finishFlag = false;

	public ProxyXmppSSLForward(ServerSocket listen_socket, ListenPort listen_info) {
		this.listen_socket = listen_socket;
		this.listen_info = listen_info;
	}

	@Override
	public void run() {
		while (!listen_socket.isClosed()) {

			try {

				Socket client = listen_socket.accept();
				log("accept");

				Socket server = new Socket();
				server.connect(listen_info.getServer().getAddress());

				skipDataUntilSSLConnectionStarted(client, server);

				SSLSocket clientSSLSocket = (SSLSocket) createSSLContext(listen_info.getServer().getIp(),
						listen_info.getCA().get()).getSocketFactory().createSocket(client, null, true);
				SSLSocket serverSSLSocket = (SSLSocket) createSSLSocketFactory().createSocket(server, null, true);
				clientSSLSocket.setUseClientMode(false);
				serverSSLSocket.setUseClientMode(true);
				createConnection(new SocketEndpoint(clientSSLSocket), new SocketEndpoint(serverSSLSocket));

			} catch (Exception e) {

				errWithStackTrace(e);
			}
		}
	}

	private void skipDataUntilSSLConnectionStarted(Socket client, Socket server) throws Exception {
		InputStream cI = client.getInputStream();
		OutputStream cO = client.getOutputStream();
		InputStream sI = server.getInputStream();
		OutputStream sO = server.getOutputStream();

		Thread clientT = new Thread(() -> {
			try {

				byte[] buff = new byte[4096];
				do {

					if (finishFlag)
						return;
					if (cI.available() > 0) {

						int len = cI.read(buff, 0, buff.length);
						if (len < 0) {

							err("ERROR: xmpp client socket closed");
							return;
						}
						String body = new String(ArrayUtils.subarray(buff, 0, len));
						// Logging.log("-->" + body);
						sO.write(buff, 0, len);
					}
					sleep(1000); // wait 1s
				} while (true);
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});

		Thread serverT = new Thread(() -> {
			try {

				byte[] buff = new byte[4096];
				do {

					if (finishFlag)
						return;
					if (sI.available() > 0) {

						int len2 = sI.read(buff, 0, buff.length);
						if (len2 < 0) {

							err("ERROR: xmpp server socket closed");
							return;
						}
						String body = new String(ArrayUtils.subarray(buff, 0, len2));
						// Logging.log("<--" + body);
						if (body.contains("proceed")) {

							finishFlag = true;
							while (clientT.isAlive()) {

								sleep(1000);
							}
						}
						cO.write(buff, 0, len2);
					}
				} while (true);
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});

		clientT.start();
		serverT.start();
		clientT.join();
		serverT.join();
		finishFlag = false;
	}

	public void createConnection(Endpoint client, Endpoint server) throws Exception {
		DuplexAsync duplex = DuplexFactory.createDuplexAsync(client, server, listen_info.getServer().getEncoder());
		duplex.start();
		DuplexManager.getInstance().registerDuplex(duplex);
	}

	public void close() throws Exception {
		listen_socket.close();
	}
}
