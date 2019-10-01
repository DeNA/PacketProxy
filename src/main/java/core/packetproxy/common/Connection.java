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
package packetproxy.common;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Connection {
	private int listen_port;
	private int proxy_port;
	private InetSocketAddress client;
	private InetSocketAddress server;
	private Direction direction;
	
	public enum Direction {
		NO_DIRECTION, CLIENT_TO_SERVER, SERVER_TO_CLIENT
	}
	
	public Connection()
	{
		this.listen_port = 0;
		this.proxy_port = 0;
		this.client = new InetSocketAddress(0);
		this.server = new InetSocketAddress(0);
		this.direction = Direction.NO_DIRECTION;
	}

	public Connection(int listen_port, int proxy_port, InetSocketAddress client, InetSocketAddress server, Direction direction)
	{
		this.listen_port = listen_port;
		this.proxy_port = proxy_port;
		this.client = client;
		this.server = server;
		this.direction = direction;
	}
	
	public Connection(int listen_port, int proxy_port, InetSocketAddress client, InetSocketAddress server)
	{
		this.listen_port = listen_port;
		this.proxy_port = proxy_port;
		this.client = client;
		this.server = server;
		this.direction = Direction.NO_DIRECTION;
	}

	public Connection(int listen_port, Socket client_socket, Socket server_socket, Direction direction)
	{
		InetSocketAddress client_addr = null;
		InetSocketAddress server_addr = null;
		int proxy_port = 0;

		if (client_socket != null) {
			InetAddress client_ip   = client_socket.getInetAddress();
			int         client_port = client_socket.getPort();
			client_addr = new InetSocketAddress(client_ip, client_port);
		}
		if (server_socket != null) {
			InetAddress server_ip   = server_socket.getInetAddress();
			int         server_port = server_socket.getPort();
			server_addr = new InetSocketAddress(server_ip, server_port);
			proxy_port = server_socket.getLocalPort();
		}

		this.listen_port = listen_port;
		this.proxy_port = proxy_port;
		this.client = client_addr;
		this.server = server_addr;
		this.direction = direction;
	}
	
	public int getListenPort() {
		return this.listen_port;
	}
	
	public int getProxyPort() {
		return this.proxy_port;
	}

	public InetAddress getClientIP() {
		return (client == null) ? null : client.getAddress();
	}

	public int getClientPort() {
		return (client == null) ? 0 : client.getPort();
	}

	public InetAddress getServerIP() {
		return (server == null) ? null : server.getAddress();
	}

	public int getServerPort() {
		return (server == null) ? 0 : server.getPort();
	}
	
	public InetSocketAddress getDestination() {
		return (direction == Direction.CLIENT_TO_SERVER) ? server : client;
	}

	public InetSocketAddress getSource() {
		return (direction == Direction.CLIENT_TO_SERVER) ? client : server;
	}
	
	public Direction getDirection() {
		return direction;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Connection)) {
			return false;
		}
		Connection conn = (Connection) obj;
		
		if (this.listen_port != conn.listen_port ||
			!this.client.equals(conn.client) ||
			!this.server.equals(conn.server) || 
			this.direction != conn.direction) {
			return false;
		}
		
		return true;
	}
}
