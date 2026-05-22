/*
 * Copyright 2026 DeNA Co., Ltd.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

public class UDPSocketEndpointTest {

	@Test
	public void testIgnorePacketFromUnexpectedSource() throws Exception {
		try (var fixture = openFixture()) {
			var request = ascii("request");
			var response = ascii("server-response");
			var intruderPayload = ascii("intruder-payload");

			fixture.sendRequestFromEndpoint(request);
			var serverReceivedPacket = fixture.receiveFromEndpoint();
			assertArrayEquals(request, getPayload(serverReceivedPacket));

			var endpointResponse = fixture.readFromEndpointAsync();
			fixture.sendUnexpectedPacket(intruderPayload);
			assertReadTimeout(endpointResponse);

			fixture.sendResponseToEndpoint(response, serverReceivedPacket);
			assertArrayEquals(response, fixture.await(endpointResponse));
		}
	}

	private TestFixture openFixture() throws Exception {
		var existingThreads = new HashSet<>(Thread.getAllStackTraces().keySet());
		var serverSocket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
		serverSocket.setSoTimeout(1000);
		var intruderSocket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
		var endpoint = new UDPSocketEndpoint(new InetSocketAddress("127.0.0.1", serverSocket.getLocalPort()));
		var endpointThreads = getNewThreads(existingThreads);
		var executor = Executors.newSingleThreadExecutor();
		return new TestFixture(serverSocket, intruderSocket, endpoint, endpointThreads, executor);
	}

	private byte[] ascii(String value) {
		return value.getBytes(StandardCharsets.US_ASCII);
	}

	private byte[] getPayload(DatagramPacket packet) {
		return Arrays.copyOf(packet.getData(), packet.getLength());
	}

	private void assertReadTimeout(Future<byte[]> future) {
		assertThrows(TimeoutException.class, () -> future.get(200, TimeUnit.MILLISECONDS));
	}

	private Set<Thread> getNewThreads(Set<Thread> existingThreads) {
		var currentThreads = Thread.getAllStackTraces().keySet();
		var newThreads = new HashSet<Thread>();
		for (var thread : currentThreads) {

			if (!existingThreads.contains(thread)) {
				newThreads.add(thread);
			}
		}
		return newThreads;
	}

	private void closeEndpoint(UDPSocketEndpoint endpoint, Set<Thread> endpointThreads) throws Exception {
		getSocket(endpoint).close();

		var pipe = getPipe(endpoint);
		pipe.getRawEndpoint().getInputStream().close();
		pipe.getRawEndpoint().getOutputStream().close();
		pipe.getProxyRawEndpoint().getInputStream().close();
		pipe.getProxyRawEndpoint().getOutputStream().close();

		for (var thread : endpointThreads) {

			thread.interrupt();
			thread.join(1000);
		}
	}

	private DatagramSocket getSocket(UDPSocketEndpoint endpoint) throws Exception {
		var socketField = UDPSocketEndpoint.class.getDeclaredField("socket");
		socketField.setAccessible(true);
		return (DatagramSocket) socketField.get(endpoint);
	}

	private PipeEndpoint getPipe(UDPSocketEndpoint endpoint) throws Exception {
		var pipeField = UDPSocketEndpoint.class.getDeclaredField("pipe");
		pipeField.setAccessible(true);
		return (PipeEndpoint) pipeField.get(endpoint);
	}

	private class TestFixture implements AutoCloseable {

		private static final int BUFFER_SIZE = 4096;
		private final DatagramSocket serverSocket;
		private final DatagramSocket intruderSocket;
		private final UDPSocketEndpoint endpoint;
		private final Set<Thread> endpointThreads;
		private final ExecutorService executor;

		private TestFixture(DatagramSocket serverSocket, DatagramSocket intruderSocket, UDPSocketEndpoint endpoint,
				Set<Thread> endpointThreads, ExecutorService executor) {
			this.serverSocket = serverSocket;
			this.intruderSocket = intruderSocket;
			this.endpoint = endpoint;
			this.endpointThreads = endpointThreads;
			this.executor = executor;
		}

		private void sendRequestFromEndpoint(byte[] payload) throws Exception {
			var endpointOutput = endpoint.getOutputStream();
			endpointOutput.write(payload);
			endpointOutput.flush();
		}

		private DatagramPacket receiveFromEndpoint() throws Exception {
			var packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
			serverSocket.receive(packet);
			return packet;
		}

		private Future<byte[]> readFromEndpointAsync() {
			return executor.submit(() -> {
				var endpointInput = endpoint.getInputStream();
				var buf = new byte[BUFFER_SIZE];
				var len = endpointInput.read(buf);
				return Arrays.copyOf(buf, len);
			});
		}

		private void sendUnexpectedPacket(byte[] payload) throws Exception {
			var packet = new DatagramPacket(payload, payload.length,
					new InetSocketAddress("127.0.0.1", endpoint.getLocalPort()));
			intruderSocket.send(packet);
		}

		private void sendResponseToEndpoint(byte[] payload, DatagramPacket requestPacket) throws Exception {
			var responsePacket = new DatagramPacket(payload, payload.length, requestPacket.getSocketAddress());
			serverSocket.send(responsePacket);
		}

		private byte[] await(Future<byte[]> future) throws Exception {
			return future.get(1, TimeUnit.SECONDS);
		}

		@Override
		public void close() throws Exception {
			executor.shutdownNow();
			closeEndpoint(endpoint, endpointThreads);
			intruderSocket.close();
			serverSocket.close();
		}
	}
}
