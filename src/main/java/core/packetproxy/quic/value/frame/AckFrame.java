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

package packetproxy.quic.value.frame;

import com.google.common.collect.ImmutableList;
import lombok.*;
import lombok.experimental.NonFinal;
import packetproxy.quic.value.frame.helper.AckRanges;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.utils.PacketNumbers;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

import java.nio.ByteBuffer;
import java.util.List;


/* RFC9000 19.3
ACK Frame {
  Type (i) = 0x02..0x03,
  Largest Acknowledged (i),
  ACK Delay (i),
  ACK Range Count (i),
  First ACK Range (i),
  ACK Range (..) ...,
  [ECN Counts (..)], // if type is 0x03
}

ACK Range {
  Gap (i),
  ACK Range Length (i),
}

ECN Counts {
  ECT0 Count (i),
  ECT1 Count (i),
  ECN-CE Count (i),
}
*/

@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class AckFrame extends Frame {

    static public final byte TYPE = 0x02;

    static public List<Byte> supportedTypes() {
        return ImmutableList.of(TYPE);
    }

    long largestAcknowledged;
    long ackDelay;
    long ackRangeCount;
    long firstAckRange;
    AckRanges ackRanges;

    static public AckFrame parse(byte[] bytes) {
        return AckFrame.parse(ByteBuffer.wrap(bytes));
    }

    static public AckFrame parse(ByteBuffer buffer) {
        byte type = buffer.get();
        long largestAcknowledged = VariableLengthInteger.parse(buffer).getValue();
        long ackDelay = VariableLengthInteger.parse(buffer).getValue();
        long ackRangeCount = VariableLengthInteger.parse(buffer).getValue();
        long firstAckRange = VariableLengthInteger.parse(buffer).getValue();
        AckRanges ackRanges = new AckRanges(buffer, ackRangeCount);
        return new AckFrame(largestAcknowledged, ackDelay, ackRangeCount, firstAckRange, ackRanges);
    }

    @SneakyThrows
    public PacketNumber getLargestAckedPn() {
        return PacketNumber.of(this.largestAcknowledged);
    }

    public PacketNumbers getAckedPacketNumbers() {
        PacketNumbers pns = new PacketNumbers();
        for (long pn = this.largestAcknowledged; pn >= this.largestAcknowledged - this.firstAckRange; pn--) {
            pns.add(PacketNumber.of(pn));
        }
        pns.addAll(ackRanges.getAckPacketNumbers(this.largestAcknowledged - this.firstAckRange - 1));
        return pns;
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(1500);
        buffer.put(TYPE);
        buffer.put(VariableLengthInteger.of(this.largestAcknowledged).getBytes());
        buffer.put(VariableLengthInteger.of(this.ackDelay).getBytes());
        buffer.put(VariableLengthInteger.of(this.ackRanges.size()).getBytes());
        buffer.put(VariableLengthInteger.of(this.firstAckRange).getBytes());
        buffer.put(ackRanges.serialize());
        buffer.flip();
        return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
    }

    @Override
    public boolean isAckEliciting() {
        return false;
    }

}
