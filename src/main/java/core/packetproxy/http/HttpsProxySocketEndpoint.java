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
package packetproxy.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import packetproxy.common.Endpoint;
import packetproxy.common.Utils;

import javax.net.ssl.SSLSocket;

public class HttpsProxySocketEndpoint implements Endpoint {
	SSLSocket proxySocket;
	InputStream proxyIn;
	OutputStream proxyOut;
	String reqTmpl = "CONNECT %s:%d HTTP/1.1\r\nHost: %s\r\n\r\n";

	public HttpsProxySocketEndpoint(InetSocketAddress proxyAddr, InetSocketAddress serverAddr) throws Exception {
		proxySocket = (SSLSocket) new Socket();
		proxySocket.connect(proxyAddr);

		proxyOut = proxySocket.getOutputStream();
		proxyOut.write(String.format(reqTmpl,
				serverAddr.getHostString(),
				serverAddr.getPort(),
				serverAddr.getHostString()).getBytes());

		proxyIn = proxySocket.getInputStream();

		int length = 0;
		byte[] input_data = new byte[1024];
		while ((length = proxyIn.read(input_data, 0, input_data.length)) != -1) {
			byte[] search_word = new String("\r\n\r\n").getBytes();
			if ((Utils.indexOf(input_data, 0, length, search_word)) >= 0) {
				break;
			}
		}
		proxySocket = Https.convertToClientSSLSocket(proxySocket);
	}

	@Override
	public InetSocketAddress getAddress() {
		return new InetSocketAddress(proxySocket.getInetAddress(), proxySocket.getPort());
	}

	@Override
	public InputStream getInputStream() throws Exception {
		return proxySocket.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() throws Exception {
		return proxySocket.getOutputStream();
	}
	
	@Override
	public String getName() {
		return null;
	}

	public String getApplicationProtocol(){ return this.proxySocket.getApplicationProtocol();}
}
