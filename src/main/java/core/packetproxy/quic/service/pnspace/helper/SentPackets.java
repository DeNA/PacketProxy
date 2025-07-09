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

package packetproxy.quic.service.pnspace.helper;

import java.util.*;
import lombok.NoArgsConstructor;
import packetproxy.quic.utils.PacketNumbers;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.SentPacket;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.value.packet.PnSpacePacket;

@NoArgsConstructor
public class SentPackets implements Iterable<SentPacket> {
	private Map<PacketNumber, SentPacket> sentPacketMap = new HashMap<>();

	public SentPackets(Collection<SentPacket> sentPacketList) {
		sentPacketList.stream().forEach(spkt -> this.sentPacketMap.put(spkt.getPacketNumber(), spkt));
	}

	public synchronized void add(SentPacket sentPacket) {
		this.sentPacketMap.put(sentPacket.getPacketNumber(), sentPacket);
	}

	public synchronized void sent(PnSpacePacket packet) {
		this.sentPacketMap.put(packet.getPacketNumber(), new SentPacket(packet));
	}

	public synchronized SentPacket get(PacketNumber pn) {
		return this.sentPacketMap.get(pn);
	}

	public synchronized boolean isEmpty() {
		return this.sentPacketMap.isEmpty();
	}

	public synchronized boolean hasAnyAckElicitingPacket() {
		return this.sentPacketMap.values().stream().anyMatch(SentPacket::isAckEliciting);
	}

	public synchronized Optional<SentPacket> getLargest() {
		return this.sentPacketMap.values().stream()
				.max(Comparator.comparingLong(spkt -> spkt.getPacketNumber().getNumber()));
	}

	public synchronized Optional<AckFrame> getLargestAckFrame() {
		return this.sentPacketMap.values().stream().filter(SentPacket::hasAckFrame).map(spkt -> spkt.getAckFrame())
				.max(Comparator.comparingLong(ackFrame -> ackFrame.get().getLargestAcknowledged())).flatMap(a -> a);
	}

	/**
	 * @return newly acked PacketNumbers
	 */
	public synchronized SentPackets detectAndRemoveAckedPackets(AckFrame ackFrame) {
		PacketNumbers ackPns = ackFrame.getAckedPacketNumbers();
		SentPackets newlyAckedPns = new SentPackets();
		ackPns.stream().forEach(pn -> {
			if (sentPacketMap.containsKey(pn)) {
				SentPacket sentPacket = sentPacketMap.remove(pn);
				newlyAckedPns.add(sentPacket);
			}
		});
		return newlyAckedPns;
	}

	public void removePacket(SentPacket sentPacket) {
		this.removePacket(sentPacket.getPacketNumber());
	}

	public synchronized void removePacket(PacketNumber packetNumber) {
		this.sentPacketMap.remove(packetNumber);
	}

	public synchronized SentPackets getUnAckedPackets() {
		return new SentPackets(new ArrayList<>(this.sentPacketMap.values()));
	}

	public synchronized void clear() {
		this.sentPacketMap.clear();
	}

	@Override
	public Iterator<SentPacket> iterator() {
		return this.sentPacketMap.values().iterator();
	}
}
