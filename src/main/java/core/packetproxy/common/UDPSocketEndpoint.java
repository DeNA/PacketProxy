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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPSocketEndpoint implements Endpoint {

	private DatagramSocket socket;
	private InetSocketAddress serverAddr;
	private PipeEndpoint pipe;
	private static int BUFSIZE = 4096;
	private final ExecutorService executor;
	private volatile boolean closed;

	public UDPSocketEndpoint(InetSocketAddress addr) throws Exception {
		socket = new DatagramSocket();
		socket.connect(addr);
		serverAddr = addr;
		pipe = new PipeEndpoint(addr);
		executor = Executors.newFixedThreadPool(2);
		closed = false;
		loop();
	}

	@Override
	public InetSocketAddress getAddress() {
		return serverAddr;
	}

	@Override
	public InputStream getInputStream() throws Exception {
		return pipe.getProxyRawEndpoint().getInputStream();
	}

	@Override
	public OutputStream getOutputStream() throws Exception {
		return pipe.getProxyRawEndpoint().getOutputStream();
	}

	private void loop() {
		Callable<Void> sendTask = new Callable<Void>() {

			public Void call() throws Exception {
				while (!closed) {

					InputStream is = pipe.getRawEndpoint().getInputStream();
					byte[] input_data = new byte[BUFSIZE];
					int len = is.read(input_data);
					if (len < 0) {

						return null;
					}
					DatagramPacket sendPacket = new DatagramPacket(input_data, 0, len, serverAddr);
					socket.send(sendPacket);
				}
				return null;
			}
		};
		Callable<Void> recvTask = new Callable<Void>() {

			public Void call() throws Exception {
				while (!closed) {

					byte[] buf = new byte[BUFSIZE];
					DatagramPacket recvPacket = new DatagramPacket(buf, BUFSIZE);
					socket.receive(recvPacket);
					OutputStream os = pipe.getRawEndpoint().getOutputStream();
					os.write(recvPacket.getData(), 0, recvPacket.getLength());
					os.flush();
				}
				return null;
			}
		};
		executor.submit(sendTask);
		executor.submit(recvTask);
	}

	public void close() {
		if (closed) {

			return;
		}
		closed = true;
		socket.close();
		executor.shutdownNow();
		try {

			pipe.getRawEndpoint().getInputStream().close();
		} catch (Exception ignored) {
		}
		try {

			pipe.getRawEndpoint().getOutputStream().close();
		} catch (Exception ignored) {
		}
		try {

			pipe.getProxyRawEndpoint().getInputStream().close();
		} catch (Exception ignored) {
		}
		try {

			pipe.getProxyRawEndpoint().getOutputStream().close();
		} catch (Exception ignored) {
		}
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
