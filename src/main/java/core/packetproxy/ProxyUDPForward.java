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

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import packetproxy.common.Endpoint;
import packetproxy.common.UDPServerSocket;
import packetproxy.common.UDPSocketEndpoint;
import packetproxy.model.ListenPort;

public class ProxyUDPForward extends Proxy {

	private static final int MAX_ACTIVE_CONNECTIONS = 256;
	private ListenPort listen_info;
	private UDPServerSocket listen_socket;
	private final Map<InetSocketAddress, ActiveConnection> activeConnections = new LinkedHashMap<>();
	private volatile boolean closed = false;

	private static class ActiveConnection {
		private final DuplexAsync duplex;
		private final UDPSocketEndpoint serverEndpoint;
		private final int duplexHash;

		ActiveConnection(DuplexAsync duplex, UDPSocketEndpoint serverEndpoint, int duplexHash) {
			this.duplex = duplex;
			this.serverEndpoint = serverEndpoint;
			this.duplexHash = duplexHash;
		}
	}

	public ProxyUDPForward(ListenPort listen_info) throws Exception {
		this.listen_info = listen_info;
		listen_socket = new UDPServerSocket(listen_info.getPort());
	}

	@Override
	public void run() {
		try {

			while (!closed) {
				try {

					Endpoint client_endpoint = listen_socket.accept();
					log("accept");

					InetSocketAddress clientAddr = client_endpoint.getAddress();
					InetSocketAddress serverAddr = listen_info.getServer().getAddress();
					UDPSocketEndpoint server_endpoint = new UDPSocketEndpoint(serverAddr);

					DuplexAsync duplex = DuplexFactory.createDuplexAsync(client_endpoint, server_endpoint,
							listen_info.getServer().getEncoder());
					duplex.start();
					int duplexHash = DuplexManager.getInstance().registerDuplex(duplex);

					closeConnectionIfExists(clientAddr);
					activeConnections.put(clientAddr, new ActiveConnection(duplex, server_endpoint, duplexHash));
					evictIfOverLimit();
				} catch (Exception e) {

					if (!closed) {
						errWithStackTrace(e);
					}
				}
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		} finally {
			closeAllConnections();
		}
	}

	public void close() throws Exception {
		closed = true;
		closeAllConnections();
		listen_socket.close();
	}

	private void evictIfOverLimit() {
		while (activeConnections.size() > MAX_ACTIVE_CONNECTIONS) {
			Iterator<Map.Entry<InetSocketAddress, ActiveConnection>> i = activeConnections.entrySet().iterator();
			if (!i.hasNext()) {

				return;
			}
			Map.Entry<InetSocketAddress, ActiveConnection> oldest = i.next();
			i.remove();
			closeConnection(oldest.getKey(), oldest.getValue());
		}
	}

	private void closeConnectionIfExists(InetSocketAddress clientAddr) {
		ActiveConnection oldConnection = activeConnections.remove(clientAddr);
		if (oldConnection != null) {

			closeConnection(clientAddr, oldConnection);
		}
	}

	private void closeConnection(InetSocketAddress clientAddr, ActiveConnection connection) {
		try {

			connection.duplex.close();
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		try {

			connection.serverEndpoint.close();
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		try {

			DuplexManager.getInstance().removeDuplex(connection.duplexHash);
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		try {

			listen_socket.removeConnection(clientAddr);
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	private void closeAllConnections() {
		for (Map.Entry<InetSocketAddress, ActiveConnection> entry : activeConnections.entrySet()) {

			closeConnection(entry.getKey(), entry.getValue());
		}
		activeConnections.clear();
	}
}
