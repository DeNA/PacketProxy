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

package packetproxy.quic.service.framegenerator.helper;

import static packetproxy.util.Logging.err;

import java.util.TreeSet;

public class ReceivedPacketNumbers {

	private TreeSet<Long> unreceivedPacketNumbers = new TreeSet<>();

	public void unreceived(long packetNumber) {
		unreceivedPacketNumbers.add(packetNumber);
	}

	public void received(long packetNumber) {
		unreceivedPacketNumbers.remove(packetNumber);
	}

	public boolean isReceived(long packetNumber) {
		return !unreceivedPacketNumbers.contains(packetNumber);
	}

	public boolean isUnreceived(long packetNumber) {
		return unreceivedPacketNumbers.contains(packetNumber);
	}

	public long getSmallestOfRange(long largestOfRange, long smallestValid) {
		assert (smallestValid <= largestOfRange);
		if (unreceivedPacketNumbers.contains(largestOfRange)) {

			err("[QUIC] Error: AckRange: %d isn't in ack_range", largestOfRange);
			return 0;
		}
		return getSmallestReceived(largestOfRange, smallestValid);
	}

	public long getSmallestOfGap(long largestOfGap, long smallestValid) {
		assert (smallestValid <= largestOfGap);
		if (!unreceivedPacketNumbers.contains(largestOfGap)) {

			err("[QUIC] Error: AckRange: %d isn't in gap", largestOfGap);
			return 0;
		}
		return getSmallestUnreceived(largestOfGap, smallestValid);
	}

	public void clearLessThan(long packetNumber) {
		unreceivedPacketNumbers.removeIf(pn -> pn < packetNumber);
	}

	private long getSmallestUnreceived(long largestOfGap, long smallestValid) {
		assert (smallestValid <= largestOfGap);
		for (long i = largestOfGap; i >= smallestValid; i--) {

			if (!unreceivedPacketNumbers.contains(i)) {

				return i + 1;
			}
		}
		return smallestValid;
	}

	private long getSmallestReceived(long largestOfRange, long smallestValid) {
		assert (smallestValid <= largestOfRange);
		if (unreceivedPacketNumbers.isEmpty()) { // 全部受信済み

			return smallestValid;
		}
		if (largestOfRange < unreceivedPacketNumbers.first()) { // 全部受信済み

			return smallestValid;
		}
		return Math.max(smallestValid, unreceivedPacketNumbers.floor(largestOfRange) + 1);
	}

}
