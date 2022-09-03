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

import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.SentPacket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

public class LostPackets implements Iterable<SentPacket> {
    private Map<PacketNumber, SentPacket> lostPackets = new HashMap<>();

    public String toString() {
        return String.format("Lost %d packets", this.lostPackets.values().size());
    }

    public void add(SentPacket sentPacket) {
        lostPackets.put(sentPacket.getPacketNumber(), sentPacket);
    }

    public boolean isEmpty() {
        return lostPackets.isEmpty();
    }

    public Stream<SentPacket> stream() {
        return lostPackets.values().stream();
    }

    @Override
    public Iterator<SentPacket> iterator() {
        return lostPackets.values().iterator();
    }

}
