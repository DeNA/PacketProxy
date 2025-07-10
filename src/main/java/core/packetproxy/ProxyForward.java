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
import packetproxy.common.Endpoint;
import packetproxy.common.EndpointFactory;
import packetproxy.common.SocketEndpoint;
import packetproxy.model.ListenPort;
import packetproxy.util.PacketProxyUtility;

public class ProxyForward extends Proxy {

	private ListenPort listen_info;
	private ServerSocket listen_socket;

	public ProxyForward(ServerSocket listen_socket, ListenPort listen_info) {
		this.listen_socket = listen_socket;
		this.listen_info = listen_info;
	}

	@Override
	public void run() {
		while (!listen_socket.isClosed()) {

			try {

				Socket client = listen_socket.accept();
				PacketProxyUtility.getInstance().packetProxyLog("accept");

				Endpoint server_e = EndpointFactory.createFromServer(listen_info.getServer());
				createConnection(new SocketEndpoint(client), server_e);
			} catch (Exception e) {

				e.printStackTrace();
			}
		}
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
