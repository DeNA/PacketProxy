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

	public UDPSocketEndpoint(InetSocketAddress addr) throws Exception {
		socket = new DatagramSocket();
		serverAddr = addr;
		pipe = new PipeEndpoint(addr);
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
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<Void> sendTask = new Callable<Void>() {
			public Void call() throws Exception {
				while (true) {
					InputStream is = pipe.getRawEndpoint().getInputStream();
					byte[] input_data = new byte[BUFSIZE];
					int len = is.read(input_data);
					DatagramPacket sendPacket = new DatagramPacket(input_data, 0, len, serverAddr);
					socket.send(sendPacket);                  
				}
			}
		};
		Callable<Void> recvTask = new Callable<Void>() {
			public Void call() throws Exception {
				while (true) {
					byte[] buf = new byte[BUFSIZE];
					DatagramPacket recvPacket = new DatagramPacket(buf, BUFSIZE);
					socket.receive(recvPacket);
					OutputStream os = pipe.getRawEndpoint().getOutputStream();
					os.write(recvPacket.getData(), 0, recvPacket.getLength());
					os.flush();
				}
			}
		};
		executor.submit(sendTask);
		executor.submit(recvTask);
	}
	
	@Override
	public String getName() {
		return null;
	}
}
