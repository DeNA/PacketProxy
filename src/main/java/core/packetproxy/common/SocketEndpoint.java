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
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketEndpoint implements Endpoint {
	Socket socket;
	InputStream inputstream;

	public SocketEndpoint(Socket socket) throws Exception {
		this.socket = socket;
		inputstream = socket.getInputStream();
	}

	public SocketEndpoint(Socket socket, InputStream lookaheadBuffer) throws Exception {
		this.socket = socket;
		inputstream = new SequenceInputStream(lookaheadBuffer, socket.getInputStream());
	}

	public SocketEndpoint(InetSocketAddress addr) throws Exception {
		socket = new Socket();
		socket.connect(addr);
		inputstream = socket.getInputStream();
	}

	public SocketEndpoint(InetSocketAddress addr, int timeout) throws Exception {
		socket = new Socket();
		socket.connect(addr, timeout);
		inputstream = socket.getInputStream();
	}

	@Override
	public InetSocketAddress getAddress() {
		return new InetSocketAddress(socket.getInetAddress(), socket.getPort());
	}

	@Override
	public InputStream getInputStream() throws Exception {
		return inputstream;
	}

	@Override
	public OutputStream getOutputStream() throws Exception {
		return socket.getOutputStream();
	}

	@Override
	public int getLocalPort() {
		return socket.getLocalPort();
	}

	@Override
	public String getName() {
		return null;
	}
}
