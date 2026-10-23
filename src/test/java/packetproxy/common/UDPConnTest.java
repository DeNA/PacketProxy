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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class UDPConnTest {

	@Test
	public void testCloseStopsAutomaticallyCreatedThread() throws Exception {
		var existingThreads = new HashSet<>(Thread.getAllStackTraces().keySet());
		var conn = new UDPConn(new InetSocketAddress("127.0.0.1", 20000));
		var queue = new LinkedBlockingQueue<DatagramPacket>();
		try {

			conn.getAutomatically(queue);
			var payload = "request".getBytes(StandardCharsets.US_ASCII);
			var endpointOutput = conn.getEndpoint().getOutputStream();
			endpointOutput.write(payload);
			endpointOutput.flush();

			var packet = queue.poll(1, TimeUnit.SECONDS);
			assertNotNull(packet);
			assertEquals("request", new String(packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII));

			var connThreads = waitForNewThreads(existingThreads);
			assertFalse(connThreads.isEmpty());

			conn.close();
			joinThreads(connThreads);
			assertFalse(hasAliveThreads(connThreads));
		} finally {

			conn.close();
		}
	}

	private Set<Thread> waitForNewThreads(Set<Thread> existingThreads) throws Exception {
		long deadlineMillis = System.currentTimeMillis() + 1000;
		Set<Thread> newThreads = Set.of();
		while (System.currentTimeMillis() < deadlineMillis) {

			newThreads = getNewThreads(existingThreads);
			if (!newThreads.isEmpty()) {

				return newThreads;
			}
			Thread.sleep(10);
		}
		return newThreads;
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

	private void joinThreads(Set<Thread> threads) throws Exception {
		for (var thread : threads) {

			thread.join(1000);
		}
	}

	private boolean hasAliveThreads(Set<Thread> threads) {
		for (var thread : threads) {

			if (thread.isAlive()) {

				return true;
			}
		}
		return false;
	}
}
