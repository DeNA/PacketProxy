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

package packetproxy.quic.value.packet;

import lombok.Value;
import lombok.experimental.NonFinal;

import java.nio.ByteBuffer;

@NonFinal
@Value
public class QuicPacket {

    byte maskedType;
    @NonFinal
    byte type; /* Packet number length bits are not included. all the bits should be cleared */
    @NonFinal
    int origPnLength;

    protected QuicPacket(ByteBuffer buffer) {
        this(buffer.get());
    }

    protected QuicPacket(byte type) {
        this.maskedType = type;
        this.type = type;
        this.origPnLength = (type & 0x03) + 1;
    }

    protected enum PacketHeaderType {
        LongHeaderType,
        ShortHeaderType,
    }

    protected void unmaskType(PacketHeaderType headerType, byte[] maskKey) {
        this.type = QuicPacket.xor(this.maskedType, headerType, maskKey);
        this.origPnLength = (this.type & 0x03) + 1;
    }

    static private byte xor(byte type, PacketHeaderType headerType, byte[] maskKey) {
        byte leftHand;
        byte rightHand;
        if (headerType == PacketHeaderType.ShortHeaderType) {
            leftHand = (byte) (type & (byte) 0xe0);
            rightHand = (byte) ((type ^ maskKey[0]) & 0x1f);
        } else { /* headerType == LongHeaderType */
            leftHand = (byte) (type & (byte) 0xf0);
            rightHand = (byte) ((type ^ maskKey[0]) & 0x0f);
        }
        return (byte) (leftHand | rightHand);
    }

    protected byte getType(int newlyPnLength) {
        byte lengthCleared = (byte) (this.type & (byte)0xfc);
        return (byte) (lengthCleared | (newlyPnLength - 1));
    }

    protected byte getMaskedType(int newlyPnLength, PacketHeaderType headerType, byte[] maskKey) {
        byte typeWithNewPnLength = this.getType(newlyPnLength);
        return QuicPacket.xor(typeWithNewPnLength, headerType, maskKey);
    }

    protected byte[] getBytes() {
        return new byte[] { this.getType() };
    }

    protected byte[] getBytes(int newlyPnLength) {
        return new byte[] { this.getType(newlyPnLength) };
    }

    protected byte[] getMaskedBytes(int newlyPnLength, PacketHeaderType headerType, byte[] maskKey) {
        return new byte[] { this.getMaskedType(newlyPnLength, headerType, maskKey) };
    }

    protected int size() {
        return 1;
    }

}
