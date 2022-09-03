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

package packetproxy.quic.value.packet.helper;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.utils.Constants.PnSpaceType;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.service.frame.FramesBuilder;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.packet.PnSpacePacket;
import packetproxy.quic.value.packet.QuicPacket;

import java.util.Optional;

import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceInitial;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
public class TestPacket extends QuicPacket implements PnSpacePacket {

    static public final byte TYPE = 0x0;

    static public boolean is(byte type) {
        return (type & (byte)0xf0) == TYPE;
    }

    static public TestPacket of(PacketNumber packetNumber, AckFrame ackFrame) {
        return new TestPacket(TYPE, packetNumber, new FramesBuilder().add(ackFrame).build());
    }

    static public TestPacket of(PacketNumber packetNumber) {
        return new TestPacket(TYPE, packetNumber, Frames.empty);
    }

    PacketNumber packetNumber;
    Frames frames;

    public TestPacket(byte type, PacketNumber packetNumber, Frames frames) {
        super(type);
        this.packetNumber = packetNumber;
        this.frames = frames;
    }

    @Override
    public boolean isAckEliciting() {
        return frames.isAckEliciting();
    }

    @Override
    public boolean hasAckFrame() {
        return frames.hasAckFrame();
    }

    @Override
    public Optional<AckFrame> getAckFrame() {
        return frames.getAckFrame();
    }

    @Override
    public PnSpaceType getPnSpaceType() {
        return PnSpaceInitial;
    }

}
