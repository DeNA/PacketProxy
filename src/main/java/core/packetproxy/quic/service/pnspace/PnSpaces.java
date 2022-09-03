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

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.ImmutablePair;
import packetproxy.quic.utils.Constants.PnSpaceType;
import packetproxy.quic.service.pnspace.level.ApplicationDataPnSpace;
import packetproxy.quic.service.pnspace.level.HandshakePnSpace;
import packetproxy.quic.service.pnspace.level.InitialPnSpace;
import packetproxy.quic.value.packet.QuicPacket;
import packetproxy.quic.service.packet.QuicPacketBuilder;
import packetproxy.quic.value.packet.longheader.pnspace.HandshakePacket;
import packetproxy.quic.value.packet.longheader.pnspace.InitialPacket;
import packetproxy.quic.value.packet.shortheader.ShortHeaderPacket;
import packetproxy.quic.service.connection.Connection;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import static packetproxy.quic.utils.Constants.PnSpaceType.*;
import static packetproxy.util.Throwing.rethrow;

public class PnSpaces {

    private final LinkedBlockingQueue<QuicPacketBuilder> sendPacketQueue = new LinkedBlockingQueue<>();
    private final PnSpace[] pnSpaces = new PnSpace[PnSpaceType.values().length];
    private final Connection conn;

    public PnSpaces(Connection conn) {
        this.conn = conn;
        this.pnSpaces[PnSpaceInitial.ordinal()] = new InitialPnSpace(conn);
        this.pnSpaces[PnSpaceHandshake.ordinal()] = new HandshakePnSpace(conn);
        this.pnSpaces[PnSpaceApplicationData.ordinal()] = new ApplicationDataPnSpace(conn);
    }

    public PnSpace getPnSpace(PnSpaceType pnSpaceType) {
        return this.pnSpaces[pnSpaceType.ordinal()];
    }

    public void receivePacket(QuicPacket packet) {
        if (packet instanceof InitialPacket) {
            this.pnSpaces[PnSpaceInitial.ordinal()].receivePacket(packet);
        } else if (packet instanceof HandshakePacket) {
            this.pnSpaces[PnSpaceHandshake.ordinal()].receivePacket(packet);
        } else if (packet instanceof ShortHeaderPacket) {
            this.pnSpaces[PnSpaceApplicationData.ordinal()].receivePacket(packet);
        }
    }

    /**
     * 送信するパケットをキューに入れる
     */
    public void addSendPackets(List<QuicPacketBuilder> packets) {
        packets.forEach(rethrow(this.sendPacketQueue::put));
    }

    /**
     * 送信するパケットをキューから取得する (Blocking)
     */
    @SneakyThrows
    public List<QuicPacket> pollSendPackets() {
        QuicPacketBuilder builder = this.sendPacketQueue.take();
        QuicPacket packet = builder.setConnectionIdPair(this.conn.getConnIdPair()).build();
        this.conn.getPnSpace(builder.getPnSpaceType()).addSentPacket(packet);
        return new ArrayList<>(List.of(packet));
    }

    public ImmutablePair<Instant, PnSpaceType> getEarliestLossTimeAndSpace() {
        Instant lossTime = this.pnSpaces[PnSpaceInitial.ordinal()].getLossTime();
        PnSpaceType space = PnSpaceInitial;

        for (PnSpaceType pnSpaceType : ImmutableList.of(PnSpaceHandshake, PnSpaceApplicationData)) {
            if (lossTime == Instant.MIN || this.pnSpaces[pnSpaceType.ordinal()].getLossTime().isBefore(lossTime)) {
                lossTime = this.pnSpaces[pnSpaceType.ordinal()].getLossTime();
                space = pnSpaceType;
            }
        }
        return ImmutablePair.of(lossTime, space);
    }

    public Instant getEarliestLossTime() {
        return this.getEarliestLossTimeAndSpace().getLeft();
    }

    public boolean hasAnyAckElicitingPacket() {
        return Arrays.stream(this.pnSpaces).anyMatch(PnSpace::hasAnyAckElicitingPacket);
    }

}
