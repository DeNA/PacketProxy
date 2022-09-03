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

import lombok.AllArgsConstructor;
import lombok.Value;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.utils.PacketNumbers;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

import java.nio.ByteBuffer;

@AllArgsConstructor
@Value
public class AckRange {
    long gap;
    long ackRangeLength;

    public AckRange(ByteBuffer buffer) {
        this.gap = VariableLengthInteger.parse(buffer).getValue();
        this.ackRangeLength = VariableLengthInteger.parse(buffer).getValue();
    }

    public long size() {
        return this.gap + this.ackRangeLength + 2;
    }

    public PacketNumbers getAckPacketNumbers(long largestGapPn) {
        long largestAckPn = largestGapPn - this.gap - 1;
        PacketNumbers pns = new PacketNumbers();
        for (long pn = largestAckPn; pn >= largestAckPn - this.ackRangeLength; pn--) {
            pns.add(PacketNumber.of(pn));
        }
        return pns;
    }

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(1500);
        buffer.put(VariableLengthInteger.of(this.gap).getBytes());
        buffer.put(VariableLengthInteger.of(this.ackRangeLength).getBytes());
        buffer.flip();
        return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
    }

    public String toString() {
        return String.format("gap:%d, ackRangeLength:%d", this.gap, this.ackRangeLength);
    }
}
