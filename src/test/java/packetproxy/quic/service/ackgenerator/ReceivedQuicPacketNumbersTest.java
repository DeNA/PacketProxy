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

package packetproxy.quic.service.ackgenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import packetproxy.quic.service.framegenerator.helper.ReceivedPacketNumbers;

class ReceivedQuicPacketNumbersTest {

	@Test
	public void smoke() throws Exception {
		ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();
		receivedPacketNumbers.received(0);
		receivedPacketNumbers.received(1);
		receivedPacketNumbers.received(2);
		receivedPacketNumbers.received(3);
		receivedPacketNumbers.unreceived(4);
		receivedPacketNumbers.unreceived(5);
		receivedPacketNumbers.unreceived(6);

		assertEquals(7, receivedPacketNumbers.getSmallestOfRange(10, 0));
		assertEquals(4, receivedPacketNumbers.getSmallestOfGap(6, 0));
		assertEquals(0, receivedPacketNumbers.getSmallestOfRange(3, 0));
	}

	@Test
	public void 渡した値がそのまま返るパターン() throws Exception {
		ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();
		receivedPacketNumbers.unreceived(5);

		assertEquals(6, receivedPacketNumbers.getSmallestOfRange(6, 0));
		assertEquals(5, receivedPacketNumbers.getSmallestOfGap(5, 0));
	}

	@Test
	public void 何も受信していない() throws Exception {
		ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();
		receivedPacketNumbers.unreceived(0);
		receivedPacketNumbers.unreceived(1);
		receivedPacketNumbers.unreceived(2);
		receivedPacketNumbers.unreceived(3);

		assertEquals(0, receivedPacketNumbers.getSmallestOfGap(3, 0));
	}

	@Test
	public void 全て受信している() throws Exception {
		ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();
		receivedPacketNumbers.received(0);
		receivedPacketNumbers.received(1);
		receivedPacketNumbers.received(2);
		receivedPacketNumbers.received(3);
		assertEquals(0, receivedPacketNumbers.getSmallestOfRange(3, 0));
	}

	@Test
	public void smallestValidで限定している() throws Exception {
		ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();
		receivedPacketNumbers.received(0);
		receivedPacketNumbers.received(1);
		receivedPacketNumbers.received(2);
		receivedPacketNumbers.received(3);
		receivedPacketNumbers.unreceived(4);
		receivedPacketNumbers.unreceived(5);
		receivedPacketNumbers.unreceived(6);

		assertEquals(7, receivedPacketNumbers.getSmallestOfRange(10, 2));
		assertEquals(4, receivedPacketNumbers.getSmallestOfGap(6, 2));
		assertEquals(5, receivedPacketNumbers.getSmallestOfGap(6, 5));
		assertEquals(2, receivedPacketNumbers.getSmallestOfRange(3, 2));
		assertEquals(3, receivedPacketNumbers.getSmallestOfRange(3, 3));
	}

	@Test
	public void largestOfGapとsmallestValidが同じ() throws Exception {
		ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();
		receivedPacketNumbers.unreceived(0);
		receivedPacketNumbers.unreceived(1);
		receivedPacketNumbers.received(2);
		receivedPacketNumbers.received(3);

		assertEquals(1, receivedPacketNumbers.getSmallestOfGap(1, 1));
		assertEquals(0, receivedPacketNumbers.getSmallestOfGap(0, 0));
	}

	@Test
	public void largestOfRangeとsmallestValidが同じ() throws Exception {
		ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();
		receivedPacketNumbers.unreceived(0);
		receivedPacketNumbers.unreceived(1);
		receivedPacketNumbers.received(2);
		receivedPacketNumbers.received(3);

		assertEquals(3, receivedPacketNumbers.getSmallestOfRange(3, 3));
		assertEquals(2, receivedPacketNumbers.getSmallestOfRange(2, 2));
	}
}
