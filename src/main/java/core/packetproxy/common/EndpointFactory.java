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
import packetproxy.http.Http;
import packetproxy.http.Https;
import packetproxy.model.CAs.CA;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Server;

public class EndpointFactory
{
	public static Endpoint createClientEndpoint(Socket socket, InputStream lookaheadBuffer) throws Exception {
		return new SocketEndpoint(socket, lookaheadBuffer);
	}
	
	public static Endpoint createClientEndpointFromHttp(Socket socket, Http http, CA ca) throws Exception {
		if (http.isProxySsl()) {
			String proxyHost = http.getProxyHost();
			Socket ssl_client = Https.convertToServerSSLSocket(socket, proxyHost, ca);
			return new SocketEndpoint(ssl_client);
		} else {
			return new SocketEndpoint(socket);
		}
	}

	public static Endpoint createClientEndpointFromHttp(Socket socket, Http http, CA ca, String TLSApplicationProtocol) throws Exception {
		if (http.isProxySsl()) {
			String proxyHost = http.getProxyHost();
			Socket ssl_client = new Socket();
			if(null==TLSApplicationProtocol){
				ssl_client = Https.convertToServerSSLSocket(socket, proxyHost, ca);
			}else{
				ssl_client = Https.convertToServerSSLSocket(socket, proxyHost, ca, TLSApplicationProtocol);
			}
			return new SocketEndpoint(ssl_client);
		} else {
			return new SocketEndpoint(socket);
		}
	}
	
	public static Endpoint createClientEndpointFromSNIServerName(Socket socket, String serverName, CA ca, InputStream is) throws Exception {
		Socket ssl_client = Https.convertToServerSSLSocket(socket, serverName, ca, is);
		return new SocketEndpoint(ssl_client);
	}
	
	public static Endpoint createFromURI(String uri) throws Exception {
		URI u = new URI(uri);
		String host = u.getHost();
		int port = u.getPort() > 0 ? u.getPort() : 80;
		if (u.getScheme().equalsIgnoreCase("https")) {
			return new SSLSocketEndpoint(new InetSocketAddress(host, port), host);
		} else if (u.getScheme().equalsIgnoreCase("http")) {
			return new SocketEndpoint(new InetSocketAddress(host, port));
		} else {
			throw new Exception(String.format("[Error] Unknown scheme!%s", u.getScheme()));
		}
	}
	
	public static Endpoint createFromOneShotPacket(OneShotPacket packet) throws Exception {
		if (packet.getUseSSL()) {
			return new SSLSocketEndpoint(packet.getServer(), packet.getServerName());
		} else {
			// nc など複数同時接続を受け付けないconnection用に10秒でtimeoutする
			return new SocketEndpoint(packet.getServer(), 10 * 1000);
		}
	}
	
	public static Endpoint createServerEndpointFromHttp(Http http) throws Exception {
		if (http.isProxySsl()) {
			return new SSLSocketEndpoint(http.getProxyAddr(), http.getProxyHost());
		} else {
			return new SocketEndpoint(http.getProxyAddr());
		}
	}

	public static Endpoint createFromServer(Server server) throws Exception {
		if (server.getUseSSL()) {
			return new SSLSocketEndpoint(server.getAddress(), server.getIp());
		} else {
			return new SocketEndpoint(server.getAddress());
		}
	}

	public static Endpoint createSSLFromName(String name) throws Exception {
		return new SSLSocketEndpoint(new InetSocketAddress(name, 443), name);
	}

	public static Endpoint createServerEndpoint(InetSocketAddress addr) throws Exception {
		return new SocketEndpoint(addr);
	}
}
