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

package packetproxy.quic.service.framegenerator;

import static packetproxy.util.Logging.err;

import java.util.ArrayList;
import java.util.List;
import packetproxy.quic.service.framegenerator.helper.ReceivedPacketNumbers;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.value.frame.helper.AckRange;
import packetproxy.quic.value.frame.helper.AckRanges;

public class AckFrameGenerator {

	private long largestAckedPn = -1;
	private long smallestValidPn = 0;
	private ReceivedPacketNumbers receivedPacketNumbers = new ReceivedPacketNumbers();

	public void received(PacketNumber packetNumber) {
		this.received(packetNumber.getNumber());
	}

	public void received(long receivedPn) {
		if (receivedPn < this.smallestValidPn) {

			return;
		}
		if (this.largestAckedPn < receivedPn) {

			for (long i = this.largestAckedPn + 1; i < receivedPn; i++) {

				this.receivedPacketNumbers.unreceived(i);
			}
			this.receivedPacketNumbers.received(receivedPn);
			this.largestAckedPn = receivedPn;
		} else if (receivedPn < this.largestAckedPn) {

			this.receivedPacketNumbers.received(receivedPn);
		}
	}

	public void confirmedAckFrame(AckFrame ackFrameConfirmedByPeer) {
		this.smallestValidPn = ackFrameConfirmedByPeer.getLargestAcknowledged() + 1;
		receivedPacketNumbers.clearLessThan(this.smallestValidPn);
		if (largestAckedPn == ackFrameConfirmedByPeer.getLargestAcknowledged()) {

			largestAckedPn = -1;
		}
	}

	private boolean ackRangeExists(long packetNumber) {
		// 最低でも2個分スペースがないと、AckRangeを生成できない
		return packetNumber >= this.smallestValidPn + 2;
	}

	public AckFrame generateAckFrame() {
		if (largestAckedPn == -1) {

			return null;
		}
		long smallestOfRange = this.receivedPacketNumbers.getSmallestOfRange(this.largestAckedPn, this.smallestValidPn);
		long firstAckRange = this.largestAckedPn - smallestOfRange;
		if (!ackRangeExists(smallestOfRange)) {

			return new AckFrame(this.largestAckedPn, 0, 0, firstAckRange, AckRanges.emptyAckRanges);
		}
		AckRanges ackRanges = generateAckRanges(smallestOfRange);

		return new AckFrame(this.largestAckedPn, 0, ackRanges.size(), firstAckRange, ackRanges);
	}

	public PacketNumber getLargestAckedPn() {
		return this.largestAckedPn == -1 ? PacketNumber.Infinite : PacketNumber.of(this.largestAckedPn);
	}

	public PacketNumber getSmallestValidPn() {
		return PacketNumber.of(this.smallestValidPn - 1);
	}

	public AckRanges generateAckRanges(long smallestOfRange) {
		List<AckRange> ackRanges = new ArrayList<>();
		while (ackRangeExists(smallestOfRange)) {

			long largestOfGap = smallestOfRange - 1;
			AckRange ackRange = generateAckRange(largestOfGap);
			if (ackRange == null) {

				break;
			}
			ackRanges.add(ackRange);
			smallestOfRange -= ackRange.size();
		}
		return new AckRanges(ackRanges);
	}

	public AckRange generateAckRange(long largestOfGap) {
		if (this.receivedPacketNumbers.isReceived(largestOfGap)) {

			err("largestGap(%d) is not a gap", largestOfGap);
			return null;
		}

		long smallestOfGap = this.receivedPacketNumbers.getSmallestOfGap(largestOfGap, this.smallestValidPn);
		long gap = largestOfGap - smallestOfGap;

		if (smallestOfGap == this.smallestValidPn) {

			return null;
		}

		long largestOfRange = smallestOfGap - 1;
		long smallestOfRange = this.receivedPacketNumbers.getSmallestOfRange(largestOfRange, this.smallestValidPn);
		long range = largestOfRange - smallestOfRange;

		return new AckRange(gap, range);
	}
}
