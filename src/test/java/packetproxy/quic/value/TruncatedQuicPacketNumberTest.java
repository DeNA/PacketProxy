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

package packetproxy.quic.value;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

class TruncatedQuicPacketNumberTest {

	@Test
	public void _0() throws Exception {
		PacketNumber packetNumber = PacketNumber.of(0);
		PacketNumber largestAckedPn = PacketNumber.Infinite;

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn.getPacketNumber(largestAckedPn));
	}

	@Test
	public void _1() throws Exception {
		PacketNumber packetNumber = PacketNumber.of(1);
		PacketNumber largestAckedPn = PacketNumber.of(0);

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn.getPacketNumber(largestAckedPn));
	}

	@Test
	public void _255() throws Exception {
		PacketNumber packetNumber = PacketNumber.of(255);
		PacketNumber largestAckedPn = PacketNumber.of(0);

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn.getPacketNumber(largestAckedPn));
	}

	@Test
	public void _256() throws Exception {
		PacketNumber packetNumber = PacketNumber.of(256);
		PacketNumber largestAckedPn = PacketNumber.of(0);

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn.getPacketNumber(largestAckedPn));
	}

	@Test
	public void _123456789() throws Exception {
		PacketNumber packetNumber = PacketNumber.of(123456789);
		PacketNumber largestAckedPn = PacketNumber.of(0);

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn.getPacketNumber(largestAckedPn));
	}

	@Test
	public void _aabbccdd() throws Exception {
		PacketNumber packetNumber = PacketNumber.of(0xaabbccddL);
		PacketNumber largestAckedPn = PacketNumber.of(0);

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn.getPacketNumber(largestAckedPn));
	}

	@Test
	public void _aabbccd0() throws Exception {
		PacketNumber packetNumber = PacketNumber.of(0xaabbccddL);
		PacketNumber largestAckedPn = PacketNumber.of(0xaabbccd0L);

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn.getPacketNumber(largestAckedPn));
	}

	@Test
	public void rfc1() throws Exception {
		/*
		 * if an endpoint has received an acknowledgment for packet 0xabe8b3 and is sending a packet with a number of 0xac5c02,
		 * there are 29,519 (0x734f) outstanding packet numbers. In order to represent at least twice this range (59,038 packets, or 0xe69e), 16 bits are required.
		 */
		PacketNumber packetNumber = PacketNumber.of(0xac5c02);
		PacketNumber largestAckedPn = PacketNumber.of(0xabe8b3);
		byte[] truncatedPnBytes = Hex.decodeHex("5c02".toCharArray());

		TruncatedPacketNumber truncatedPn = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertArrayEquals(truncatedPnBytes, truncatedPn.getBytes());
	}

	@Test
	public void rfc2() throws Exception {
		/*
		 * if the highest successfully authenticated packet had a packet number of 0xa82f30ea, * then a packet containing a 16-bit value of 0x9b32 will be decoded as 0xa82f9b32
		 */
		byte[] truncatedPnBytes = Hex.decodeHex("9b32".toCharArray());
		PacketNumber packetNumber = PacketNumber.of(0xa82f9b32L);
		PacketNumber largestAckedPn = PacketNumber.of(0xa82f30eaL);

		TruncatedPacketNumber truncatedPn1 = new TruncatedPacketNumber(truncatedPnBytes);
		TruncatedPacketNumber truncatedPn2 = new TruncatedPacketNumber(packetNumber, largestAckedPn);
		assertEquals(packetNumber, truncatedPn1.getPacketNumber(largestAckedPn));
		assertEquals(packetNumber, truncatedPn2.getPacketNumber(largestAckedPn));
		assertEquals(truncatedPn1, truncatedPn2);
	}

}
