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

package packetproxy.quic.value.frame.helper;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import packetproxy.quic.utils.PacketNumbers;
import packetproxy.quic.value.SimpleBytes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Getter
@EqualsAndHashCode
public class AckRanges implements Iterable<AckRange> {
    static public final AckRanges emptyAckRanges = new AckRanges();

    private List<AckRange> ackRanges;

    public AckRanges(ByteBuffer buffer, long rangeCount) {
        this.ackRanges = new ArrayList<>();
        for (long i = 0; i < rangeCount; i++) {
            this.ackRanges.add(new AckRange(buffer));
        }
    }

    public AckRanges(List<AckRange> ackRanges) {
        this.ackRanges = ackRanges;
    }

    private AckRanges() {
        this.ackRanges = Collections.emptyList();
    }

    public PacketNumbers getAckPacketNumbers(long largestGapPn) {
        PacketNumbers pns = new PacketNumbers();
        for (AckRange ackRange : ackRanges) {
            pns.addAll(ackRange.getAckPacketNumbers(largestGapPn));
            largestGapPn -= ackRange.size();
        }
        return pns;
    }

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(1500);
        for (AckRange ackRange: this.ackRanges) {
            buffer.put(ackRange.serialize());
        }
        buffer.flip();
        return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
    }

    public String toString() {
        String rangeMsg = "";
        for (AckRange ackRange: this.ackRanges) {
            rangeMsg += ackRange + "|";
        }
        return "{" + rangeMsg + "}";
    }

    public int size() {
        return this.ackRanges.size();
    }

    public AckRange get(int index) {
        return this.ackRanges.get(0);
    }

    @Override
    public Iterator<AckRange> iterator() {
        return this.ackRanges.iterator();
    }
}
