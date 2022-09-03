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

package packetproxy.quic.value;

import lombok.Value;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.value.packet.PnSpacePacket;

import java.time.Instant;
import java.util.Optional;

@Value
public class SentPacket {

    PacketNumber packetNumber;
    Instant timeSent;
    PnSpacePacket packet;

    public SentPacket(PnSpacePacket packet) {
        this.packet = packet;
        this.packetNumber = packet.getPacketNumber();
        this.timeSent = Instant.now();
    }

    public boolean isAckEliciting() {
        return this.packet.isAckEliciting();
    }

    public boolean hasAckFrame() {
        return this.packet.hasAckFrame();
    }

    public Optional<AckFrame> getAckFrame() {
        return this.packet.getAckFrame();
    }
}
