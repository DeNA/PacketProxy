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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServerSocket
{
	private DatagramSocket socket;
	private UDPConnManager connManager;
	
	public UDPServerSocket(int port) throws Exception {
		socket = new DatagramSocket(port);
		connManager = new UDPConnManager();
		createRecvLoop();
	}
	
	public void close() throws Exception {
		socket.close();
	}
	
	public Endpoint accept() throws Exception {
		return connManager.accept();
	}
	
	private void createRecvLoop() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<Void> recvTask = new Callable<Void>() {
			public Void call() throws Exception {
				while (true) {
					byte[] buf = new byte[4096];
					DatagramPacket recvPacket = new DatagramPacket(buf, 4096);
					socket.receive(recvPacket);
					connManager.put(recvPacket);
				}
			}
		};
		Callable<Void> sendTask = new Callable<Void>() {
			public Void call() throws Exception {
				while (true) {
					DatagramPacket sendPacket = connManager.get();
					socket.send(sendPacket);
				}
			}
		};
		executor.submit(recvTask);
		executor.submit(sendTask);
	}
}
