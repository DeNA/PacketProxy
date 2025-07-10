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
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.PrivateDNSClient;
import packetproxy.quic.service.handshake.ClientHandshake;
import packetproxy.quic.utils.AwaitingException;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.ConnectionIdPair;

@Getter
public class ServerConnection extends Connection {

	private final ClientHandshake handshake;
	private final String serverName;

	public ServerConnection(ConnectionIdPair connIdPair, String serverName, int serverPort) throws Exception {
		super(Constants.Role.CLIENT, connIdPair, connIdPair.getDestConnId(), new DatagramSocket(),
				new InetSocketAddress(PrivateDNSClient.getByName(serverName), serverPort));
		this.handshake = new ClientHandshake(this);
		this.serverName = serverName;
		this.handshake.start(serverName);
		super.executor.submit(new RecvUdpPacketsLoop());
		super.start();
	}

	/* サーバから受信 -> 処理 -> SendPacketキュー */
	public class RecvUdpPacketsLoop implements Runnable {

		@Override
		@SneakyThrows
		public void run() {
			while (true) {

				DatagramPacket udpPacket = recvUdpPacket(); // Blocking here
				awaitingReceivedPackets.put(udpPacket);
				awaitingReceivedPackets.forEachAndRemovedIfReturnTrue(packet -> {

					try {

						serverPacketParser.parseOnePacket(packet);
						return true;
					} catch (AwaitingException e) {

						return false;
					} catch (Exception e) {

						e.printStackTrace();
						return false;
					}
				});
			}
		}
	}

	private DatagramPacket recvUdpPacket() throws Exception {
		byte[] buf = new byte[4096];
		DatagramPacket recvPacket = new DatagramPacket(buf, 4096);
		socket.receive(recvPacket);
		recvPacket.setData(ArrayUtils.subarray(recvPacket.getData(), 0, recvPacket.getLength()));
		return recvPacket;
	}

	@Override
	public boolean peerCompletedAddressValidation() {
		// Servers complete address validation when a protected packet is received.
		return this.handshakeState.isAckReceived();
	}

	@Override
	public String getName() {
		return this.serverName;
	}

}
