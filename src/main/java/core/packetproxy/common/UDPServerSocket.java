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

import static packetproxy.util.Logging.errWithStackTrace;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class UDPServerSocket {

	private static final int MAX_DATAGRAM_SIZE = 65535;
	private static final int SOCKET_BUFFER_SIZE = 1024 * 1024;
	private final DatagramSocket socket;
	private final BlockingQueue<DatagramPacket> incomingQueue;
	private final BlockingQueue<DatagramPacket> outgoingQueue;
	private final Thread recvThread;
	private final ExecutorService executor;

	public UDPServerSocket(int port) throws Exception {
		socket = new DatagramSocket(port);
		socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
		socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
		incomingQueue = new LinkedBlockingQueue<DatagramPacket>();
		outgoingQueue = new LinkedBlockingQueue<DatagramPacket>();
		recvThread = new Thread(new Runnable() {

			@Override
			public void run() {
				try {

					createRecvTask().call();
				} catch (Exception e) {

					if (!socket.isClosed()) {

						errWithStackTrace(e);
					}
				}
			}
		}, "udp-listen-recv");
		recvThread.setDaemon(true);
		recvThread.setPriority(Thread.MAX_PRIORITY);
		executor = Executors.newSingleThreadExecutor();
		createRecvLoop();
	}

	public void close() throws Exception {
		socket.close();
		executor.shutdownNow();
	}

	public DatagramPacket takeReceivedPacket() throws Exception {
		return incomingQueue.poll(100, TimeUnit.MILLISECONDS);
	}

	public void sendToClient(DatagramPacket packet) throws Exception {
		outgoingQueue.put(packet);
	}

	private void createRecvLoop() throws Exception {
		Callable<Void> sendTask = new Callable<Void>() {

			public Void call() throws Exception {
				while (true) {
					try {

						DatagramPacket sendPacket = outgoingQueue.take();
						socket.send(sendPacket);
					} catch (InterruptedException e) {

						Thread.currentThread().interrupt();
						return null;
					} catch (SocketException e) {

						if (socket.isClosed()) {

							return null;
						}
						errWithStackTrace(e);
					} catch (Exception e) {

						if (socket.isClosed()) {

							return null;
						}
						errWithStackTrace(e);
					}
				}
			}
		};
		recvThread.start();
		executor.submit(sendTask);
	}

	private Callable<Void> createRecvTask() {
		return new Callable<Void>() {

			public Void call() throws Exception {
				while (true) {
					try {

						byte[] buf = new byte[MAX_DATAGRAM_SIZE];
						DatagramPacket recvPacket = new DatagramPacket(buf, MAX_DATAGRAM_SIZE);
						socket.receive(recvPacket);
						incomingQueue.put(recvPacket);
					} catch (InterruptedException e) {

						Thread.currentThread().interrupt();
						return null;
					} catch (SocketException e) {

						if (socket.isClosed()) {

							return null;
						}
						errWithStackTrace(e);
					} catch (Exception e) {

						if (socket.isClosed()) {

							return null;
						}
						errWithStackTrace(e);
					}
				}
			}
		};
	}
}
