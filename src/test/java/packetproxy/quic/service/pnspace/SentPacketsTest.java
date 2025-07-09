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

package packetproxy.quic.service.pnspace;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import packetproxy.quic.service.pnspace.helper.SentPackets;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.SentPacket;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.value.frame.helper.AckRanges;
import packetproxy.quic.value.packet.helper.TestPacket;

class SentPacketsTest {

	private SentPackets sentPackets;

	@BeforeEach
	void beforeEach() throws Exception {
		this.sentPackets = new SentPackets();
	}

	@Test
	void getLargestAckFrameが動作すること() throws Exception {
		this.sentPackets.add(
				new SentPacket(TestPacket.of(PacketNumber.of(0), new AckFrame(0, 0, 0, 0, AckRanges.emptyAckRanges))));
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(1))));
		this.sentPackets.add(
				new SentPacket(TestPacket.of(PacketNumber.of(2), new AckFrame(2, 0, 0, 0, AckRanges.emptyAckRanges))));
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(3))));
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(4))));
		this.sentPackets.add(
				new SentPacket(TestPacket.of(PacketNumber.of(5), new AckFrame(1, 0, 0, 0, AckRanges.emptyAckRanges))));
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(6))));

		Optional<AckFrame> a = sentPackets.getLargestAckFrame();
		assertThat(a).hasValue(new AckFrame(2, 0, 0, 0, AckRanges.emptyAckRanges));
	}

	@Test
	void getLargestAckFrameがEmptyになること() throws Exception {
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(1))));
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(3))));
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(4))));
		this.sentPackets.add(new SentPacket(TestPacket.of(PacketNumber.of(6))));

		assertThat(sentPackets.getLargestAckFrame()).isEqualTo(Optional.empty());
	}
}
