/*
 * Copyright 2022 DeNA Co., Ltd.
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

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.*;
import lombok.Value;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Endpoint;
import packetproxy.common.EndpointFactory;
import packetproxy.common.StringUtils;
import packetproxy.http.Http;
import packetproxy.model.ListenPort;
import packetproxy.model.Server;
import packetproxy.model.Servers;
import packetproxy.util.PacketProxyUtility;

public class ProxyHttpTransparent extends Proxy {
	private ListenPort listen_info;
	private ServerSocket listen_socket;

	public ProxyHttpTransparent(ServerSocket listen_socket, ListenPort listen_info) throws Exception {
		this.listen_socket = listen_socket;
		this.listen_info = listen_info;
	}

	public void close() throws Exception {
		listen_socket.close();
	}

	@Override
	public void run() {
		while (!listen_socket.isClosed()) {
			try {
				Socket client = listen_socket.accept();
				PacketProxyUtility.getInstance().packetProxyLog("[ProxyHttpTransparent]: accept");
				createHttpTransparentProxy(client);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Value
	static class HostPort {
		String hostName;
		int port;

		InetSocketAddress getInetSocketAddress() throws Exception {
			return new InetSocketAddress(PrivateDNSClient.getByName(this.hostName), this.port);
		}
	}

	private HostPort parseHostName(byte[] buffer) throws Exception {
		int start = 0;
		if ((start = StringUtils.binaryFind(buffer, "Host:".getBytes())) > 0) {
			start += 5;
		} else if ((start = StringUtils.binaryFind(buffer, "host:".getBytes())) > 0) {
			start += 5;
		} else {
			throw new Exception("Host: header field is not found in beginning of 4096 bytes of packets.");
		}
		int end = StringUtils.binaryFind(buffer, "\n".getBytes(), start);
		String serverCand = new String(ArrayUtils.subarray(buffer, start, end));
		String server = "";
		int port = 80;
		Pattern pattern = Pattern.compile("^ *([^:\n\r]+)(?::([0-9]+))?");
		Matcher matcher = pattern.matcher(serverCand);
		if (matcher.find()) {
			if (matcher.group(1) != null)
				server = matcher.group(1);
			if (matcher.group(2) != null)
				port = Integer.parseInt(matcher.group(2));
		} else {
			throw new Exception("Host: header field format is not recognized.");
		}
		return new HostPort(server, port);
	}

	private void createHttpTransparentProxy(Socket client) throws Exception {
		InputStream ins = client.getInputStream();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		HostPort hostPort = null;

		byte[] input_data = new byte[4096];
		int length = 0;
		while ((length = ins.read(input_data, 0, input_data.length)) != -1) {
			bout.write(input_data, 0, length);
			int accepted_input_size = 0;
			if (bout.size() > 0 && (accepted_input_size = Http.parseHttpDelimiter(bout.toByteArray())) > 0) {
				hostPort = parseHostName(ArrayUtils.subarray(bout.toByteArray(), 0, accepted_input_size));
				break;
			}
		}
		if (hostPort == null) {
			PacketProxyUtility.getInstance().packetProxyLogErr(new String(input_data));
			PacketProxyUtility.getInstance().packetProxyLogErr("bout length == " + bout.size());
			if (bout.size() == 0) {
				PacketProxyUtility.getInstance().packetProxyLogErr("empty request!!");
				return;
			}
			PacketProxyUtility.getInstance().packetProxyLogErr("HTTP Host field is not found.");
			return;
		}

		ByteArrayInputStream lookaheadBuffer = new ByteArrayInputStream(bout.toByteArray());

		try {
			Endpoint client_e = EndpointFactory.createClientEndpoint(client, lookaheadBuffer);

			Endpoint server_e = null;
			if (listen_info.getServer() != null) { // upstream proxy
				server_e = EndpointFactory.createServerEndpoint(listen_info.getServer().getAddress());
			} else {
				server_e = EndpointFactory.createServerEndpoint(hostPort.getInetSocketAddress());
			}

			Server server = Servers.getInstance().queryByHostNameAndPort(hostPort.getHostName(), listen_info.getPort());
			createConnection(client_e, server_e, server);
		} catch (ConnectException e) {
			InetSocketAddress addr = hostPort.getInetSocketAddress();
			PacketProxyUtility.getInstance()
					.packetProxyLog("Connection Refused: " + addr.getHostName() + ":" + addr.getPort());
			e.printStackTrace();
		}
	}

	public void createConnection(Endpoint client_e, Endpoint server_e, Server server) throws Exception {
		DuplexAsync duplex = null;
		if (server == null)
			duplex = DuplexFactory.createDuplexAsync(client_e, server_e, "HTTP");
		else
			duplex = DuplexFactory.createDuplexAsync(client_e, server_e, server.getEncoder());
		duplex.start();
		// DuplexManager.getInstance().registerDuplex(duplex);
	}
}
