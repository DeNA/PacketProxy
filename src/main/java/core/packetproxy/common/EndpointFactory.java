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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

import javax.net.ssl.SSLSocket;

import packetproxy.http.Https;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Server;
import packetproxy.model.CAs.CA;

public class EndpointFactory
{
	public static Endpoint createClientEndpoint(Socket socket, InputStream lookaheadBuffer) throws Exception {
		return new SocketEndpoint(socket, lookaheadBuffer);
	}
	
	public static SSLSocketEndpoint[] createBothSideSSLEndpoints(Socket clientSocket, InputStream lookahead, InetSocketAddress serverAddr, InetSocketAddress upstreamProxyAddr, String serverName, CA ca) throws Exception {
		SSLSocket[] sslSockets = null;
		SSLSocketEndpoint[] endpoints = null;
		if (upstreamProxyAddr != null) {
			sslSockets = Https.createBothSideSSLSockets(clientSocket, lookahead, serverAddr, upstreamProxyAddr, serverName, ca);
			SSLSocketEndpoint clientEndpoint = new SSLSocketEndpoint(sslSockets[0], serverName);
			SSLSocketEndpoint serverEndpoint = new SSLSocketEndpoint(sslSockets[1], serverName);
			endpoints = new SSLSocketEndpoint[] { clientEndpoint, serverEndpoint };
		} else {
			sslSockets = Https.createBothSideSSLSockets(clientSocket, lookahead, serverAddr, null, serverName, ca);
			SSLSocketEndpoint clientEndpoint = new SSLSocketEndpoint(sslSockets[0], serverName);
			SSLSocketEndpoint serverEndpoint = new SSLSocketEndpoint(sslSockets[1], serverName);
			endpoints = new SSLSocketEndpoint[] { clientEndpoint, serverEndpoint };
		}
		return endpoints;
	}
	
	public static SSLSocketEndpoint createClientEndpointFromSNIServerName(Socket socket, String serverName, CA ca, InputStream is, String[] alpns) throws Exception {
		SSLSocket ssl_client = Https.convertToServerSSLSocket(socket, serverName, ca, is, alpns);
		return new SSLSocketEndpoint(ssl_client, serverName);
	}

	public static SSLSocketEndpoint createClientEndpointFromSNIServerName(Socket socket, String serverName, CA ca, InputStream is) throws Exception {
		SSLSocket ssl_client = Https.convertToServerSSLSocket(socket, serverName, ca, is);
		return new SSLSocketEndpoint(ssl_client, serverName);
	}
	
	public static Endpoint createFromURI(String uri) throws Exception {
		URI u = new URI(uri);
		String host = u.getHost();
		int port = u.getPort() > 0 ? u.getPort() : 80;
		if (u.getScheme().equalsIgnoreCase("https")) {
			return new SSLSocketEndpoint(new InetSocketAddress(host, port), host, null);
		} else if (u.getScheme().equalsIgnoreCase("http")) {
			return new SocketEndpoint(new InetSocketAddress(host, port));
		} else {
			throw new Exception(String.format("[Error] Unknown scheme!%s", u.getScheme()));
		}
	}
	
	public static Endpoint createFromOneShotPacket(OneShotPacket packet) throws Exception {
		if (packet.getUseSSL()) {
			return new SSLSocketEndpoint(packet.getServer(), packet.getServerName(), packet.getAlpn());
		} else {
			// nc など複数同時接続を受け付けないconnection用に10秒でtimeoutする
			return new SocketEndpoint(packet.getServer(), 10 * 1000);
		}
	}
	
	public static Endpoint createFromServer(Server server) throws Exception {
		if (server.getUseSSL()) {
			return new SSLSocketEndpoint(server.getAddress(), server.getIp(), null);
		} else {
			return new SocketEndpoint(server.getAddress());
		}
	}

	public static Endpoint createServerEndpoint(InetSocketAddress addr) throws Exception {
		return new SocketEndpoint(addr);
	}
}
