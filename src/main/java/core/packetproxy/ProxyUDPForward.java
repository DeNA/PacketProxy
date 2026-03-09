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
package packetproxy;

import static packetproxy.util.Logging.errWithStackTrace;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import packetproxy.common.UDPServerSocket;
import packetproxy.model.ListenPort;

public class ProxyUDPForward extends Proxy {

	private static final int MAX_DATAGRAM_SIZE = 65535;
	private static final long IDLE_SESSION_TIMEOUT_MILLIS = 10;
	private final ListenPort listen_info;
	private final UDPServerSocket listen_socket;
	private final Selector selector;
	private final Thread responseLoopThread;
	private final Map<InetSocketAddress, UpstreamSession> activeSessions = new LinkedHashMap<>();
	private volatile boolean closed = false;

	private static class UpstreamSession {
		private final InetSocketAddress clientAddr;
		private final InetSocketAddress serverAddr;
		private final DatagramChannel channel;
		private volatile long lastActivityAtMillis;

		UpstreamSession(InetSocketAddress clientAddr, InetSocketAddress serverAddr, DatagramChannel channel) {
			this.clientAddr = clientAddr;
			this.serverAddr = serverAddr;
			this.channel = channel;
			this.lastActivityAtMillis = System.currentTimeMillis();
		}

		private void touch() {
			lastActivityAtMillis = System.currentTimeMillis();
		}

		private boolean isIdle(long timeoutMillis) {
			return System.currentTimeMillis() - lastActivityAtMillis >= timeoutMillis;
		}
	}

	public ProxyUDPForward(ListenPort listen_info) throws Exception {
		this.listen_info = listen_info;
		listen_socket = new UDPServerSocket(listen_info.getPort());
		selector = Selector.open();
		responseLoopThread = new Thread(new Runnable() {

			@Override
			public void run() {
				runResponseLoop();
			}
		}, "udp-forward-response");
		responseLoopThread.setDaemon(true);
		responseLoopThread.start();
	}

	@Override
	public void run() {
		try {
			while (!closed) {
				try {
					DatagramPacket clientPacket = listen_socket.takeReceivedPacket();
					if (clientPacket == null) {

						continue;
					}
					InetSocketAddress clientAddr = new InetSocketAddress(clientPacket.getAddress(),
							clientPacket.getPort());
					InetSocketAddress serverAddr = listen_info.getServer().getAddress();
					closeIdleSessions();
					UpstreamSession session = getOrCreateSession(clientAddr, serverAddr);
					sendToUpstream(session, clientPacket);
				} catch (Exception e) {
					if (!closed) {
						errWithStackTrace(e);
					}
				}
			}
		} catch (Exception e) {
			errWithStackTrace(e);
		} finally {
			closeAllSessions();
		}
	}

	public void close() throws Exception {
		closed = true;
		selector.wakeup();
		closeAllSessions();
		listen_socket.close();
	}

	private UpstreamSession getOrCreateSession(InetSocketAddress clientAddr, InetSocketAddress serverAddr)
			throws Exception {
		synchronized (activeSessions) {
			UpstreamSession session = activeSessions.get(clientAddr);
			if (session != null) {

				return session;
			}

			DatagramChannel channel = DatagramChannel.open();
			channel.bind(null);
			channel.configureBlocking(false);
			UpstreamSession newSession = new UpstreamSession(clientAddr, serverAddr, channel);
			selector.wakeup();
			channel.register(selector, SelectionKey.OP_READ, newSession);
			activeSessions.put(clientAddr, newSession);
			return newSession;
		}
	}

	private void sendToUpstream(UpstreamSession session, DatagramPacket clientPacket) throws Exception {
		ByteBuffer buf = ByteBuffer.wrap(clientPacket.getData(), 0, clientPacket.getLength());
		while (buf.hasRemaining() && !closed) {
			int written = session.channel.send(buf, session.serverAddr);
			if (written > 0) {

				session.touch();
				return;
			}
			Thread.yield();
		}
	}

	private void runResponseLoop() {
		try {
			while (!closed) {
				selector.select(50);
				processSelectedResponses();
				closeIdleSessions();
			}
		} catch (Exception e) {
			if (!closed) {

				errWithStackTrace(e);
			}
		}
	}

	private void processSelectedResponses() throws Exception {
		Iterator<SelectionKey> i = selector.selectedKeys().iterator();
		while (i.hasNext()) {
			SelectionKey key = i.next();
			i.remove();
			if (!key.isValid() || !key.isReadable()) {

				continue;
			}

			UpstreamSession session = (UpstreamSession) key.attachment();
			ByteBuffer buf = ByteBuffer.allocate(MAX_DATAGRAM_SIZE);
			int len = session.channel.read(buf);
			if (len <= 0) {

				continue;
			}
			session.touch();
			byte[] response = Arrays.copyOf(buf.array(), len);
			listen_socket.sendToClient(new DatagramPacket(response, len, session.clientAddr));
		}
	}

	private void closeIdleSessions() {
		synchronized (activeSessions) {
			Iterator<Map.Entry<InetSocketAddress, UpstreamSession>> i = activeSessions.entrySet().iterator();
			while (i.hasNext()) {
				Map.Entry<InetSocketAddress, UpstreamSession> entry = i.next();
				if (!entry.getValue().isIdle(IDLE_SESSION_TIMEOUT_MILLIS)) {

					continue;
				}
				i.remove();
				closeSession(entry.getValue());
			}
		}
	}

	private void closeAllSessions() {
		synchronized (activeSessions) {
			for (UpstreamSession session : activeSessions.values()) {

				closeSession(session);
			}
			activeSessions.clear();
		}
	}

	private void closeSession(UpstreamSession session) {
		try {

			SelectionKey key = session.channel.keyFor(selector);
			if (key != null) {

				key.cancel();
			}
			session.channel.close();
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}
}
