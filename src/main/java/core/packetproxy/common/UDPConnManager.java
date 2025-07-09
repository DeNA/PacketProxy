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
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UDPConnManager {
	private Map<InetSocketAddress, UDPConn> connList;
	private BlockingQueue<InetSocketAddress> acceptedQueue;
	private BlockingQueue<DatagramPacket> recvQueue;

	public UDPConnManager() {
		connList = new HashMap<InetSocketAddress, UDPConn>();
		acceptedQueue = new LinkedBlockingQueue<InetSocketAddress>();
		recvQueue = new LinkedBlockingQueue<DatagramPacket>();
	}

	public Endpoint accept() throws Exception {
		InetSocketAddress addr = acceptedQueue.take();
		return connList.get(addr).getEndpoint();
	}

	public void put(DatagramPacket packet) throws Exception {
		InetSocketAddress addr = new InetSocketAddress(packet.getAddress(), packet.getPort());
		UDPConn conn = this.query(addr);
		if (conn == null) {
			conn = this.create(addr);
			conn.getAutomatically(recvQueue);
			acceptedQueue.put(addr);
		}
		conn.put(packet.getData(), 0, packet.getLength());
	}

	public DatagramPacket get() throws Exception {
		return recvQueue.take();
	}

	private UDPConn query(InetSocketAddress key) {
		return connList.get(key);
	}

	private UDPConn create(InetSocketAddress key) throws Exception {
		UDPConn conn = new UDPConn(key);
		connList.put(key, conn);
		return conn;
	}
}
