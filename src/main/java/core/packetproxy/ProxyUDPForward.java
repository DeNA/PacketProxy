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
import packetproxy.common.Endpoint;
import packetproxy.common.UDPServerSocket;
import packetproxy.common.UDPSocketEndpoint;
import packetproxy.model.ListenPort;
import packetproxy.util.PacketProxyUtility;

public class ProxyUDPForward extends Proxy
{
	private ListenPort listen_info;
	private UDPServerSocket listen_socket;

	public ProxyUDPForward(ListenPort listen_info) throws Exception {
		this.listen_info = listen_info;
		listen_socket = new UDPServerSocket(listen_info.getPort());
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				Endpoint client_endpoint = listen_socket.accept();
				PacketProxyUtility.getInstance().packetProxyLog("accept");

				InetSocketAddress serverAddr = listen_info.getServer().getAddress();
				UDPSocketEndpoint server_endpoint = new UDPSocketEndpoint(serverAddr);

				DuplexAsync duplex = DuplexFactory.createDuplexAsync(client_endpoint, server_endpoint, listen_info.getServer().getEncoder());
				duplex.start();
				DuplexManager.getInstance().registerDuplex(duplex);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() throws Exception {
		listen_socket.close();
	}
}
