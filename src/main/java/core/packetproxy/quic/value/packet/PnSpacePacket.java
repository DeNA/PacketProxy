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

import packetproxy.quic.utils.Constants.PnSpaceType;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.value.PacketNumber;

import java.util.Optional;

public interface PnSpacePacket {
    PacketNumber getPacketNumber();

    boolean isAckEliciting();

    boolean hasAckFrame();

    Optional<AckFrame> getAckFrame();

    PnSpaceType getPnSpaceType();

    Frames getFrames();
}
