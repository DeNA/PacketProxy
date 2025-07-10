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

package packetproxy.quic.service.connection;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.model.CAs.CA;
import packetproxy.quic.service.packet.QuicPacketParser;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.ConnectionIdPair;

@Getter
public class ClientConnections {

	private final Map<ConnectionId, ClientConnection> connes = new HashMap<>();
	private final List<ConnectionId> alreadyReceivedInitialSecrets = new ArrayList<>();
	private final ExecutorService executor = Executors.newFixedThreadPool(2);
	private final int listenPort;
	private final CA ca;
	private DatagramSocket socket;

	public ClientConnections(int listenPort, CA ca) {
		this.listenPort = listenPort;
		this.ca = ca;
		try {

			this.socket = new DatagramSocket(listenPort);
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	public void close() {
		if (!this.socket.isClosed()) {

			this.connes.values().forEach(Connection::close);
			this.executor.shutdownNow();
			this.socket.close();
		}
	}

	/**
	 * Blocking until accepting UDP packet.
	 */
	public ClientConnection accept() throws Exception {
		while (true) {

			DatagramPacket udpPacket = recvUdpPacket();
			ConnectionId destConnId = QuicPacketParser.getDestConnectionId(udpPacket.getData());

			if (find(destConnId).isPresent()) {

				find(destConnId).get().recvUdpPacket(udpPacket);
			} else {

				Optional<ClientConnection> conn = this.create(destConnId,
						new InetSocketAddress(udpPacket.getAddress(), udpPacket.getPort()));
				if (conn.isPresent()) {

					conn.get().recvUdpPacket(udpPacket);
					return conn.get();
				}
			}
		}
	}

	public Optional<ClientConnection> find(ConnectionId destConnId) {
		ClientConnection conn = this.connes.get(destConnId);
		return (conn != null) ? Optional.of(conn) : Optional.empty();
	}

	public Optional<ClientConnection> create(ConnectionId initialSecret, InetSocketAddress peerAddr) throws Exception {
		if (alreadyReceivedInitialSecrets.contains(initialSecret)) {

			/* 以前受信したことのある initialSecret を受信した。再送を意味するので無視 */
			return Optional.empty();
		}
		alreadyReceivedInitialSecrets.add(initialSecret);
		ConnectionIdPair connIdPair = ConnectionIdPair.generateRandom();
		ClientConnection conn = new ClientConnection(connIdPair, initialSecret, this.socket, peerAddr, this.ca,
				this.listenPort);
		connes.put(connIdPair.getSrcConnId(), conn);
		return Optional.of(conn);
	}

	private DatagramPacket recvUdpPacket() throws Exception {
		byte[] buf = new byte[4096];
		DatagramPacket udpPacket = new DatagramPacket(buf, 4096);
		this.socket.receive(udpPacket);
		udpPacket.setData(ArrayUtils.subarray(udpPacket.getData(), 0, udpPacket.getLength()));
		return udpPacket;
	}

}
