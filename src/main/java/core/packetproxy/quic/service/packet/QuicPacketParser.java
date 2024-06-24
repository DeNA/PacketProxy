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

package packetproxy.quic.service.packet;

import packetproxy.quic.service.connection.Connection;
import packetproxy.quic.service.key.RoleKeys;
import packetproxy.quic.utils.AwaitingException;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.packet.QuicPacket;
import packetproxy.quic.value.packet.longheader.LongHeaderPacket;
import packetproxy.quic.value.packet.longheader.pnspace.HandshakePacket;
import packetproxy.quic.value.packet.longheader.pnspace.InitialPacket;
import packetproxy.quic.value.packet.longheader.pnspace.ZeroRttPacket;
import packetproxy.quic.value.packet.shortheader.ShortHeaderPacket;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Optional;

import static packetproxy.quic.utils.Constants.PnSpaceType.*;


public class QuicPacketParser {

    static public ConnectionId getDestConnectionId(byte[] bytes) throws Exception {
        return getDestConnectionId(ByteBuffer.wrap(bytes));
    }

    static public ConnectionId getDestConnectionId(ByteBuffer buffer) throws Exception {
        byte type = getTypeWithoutIncrement(buffer);
        if (LongHeaderPacket.is(type)) {
            return LongHeaderPacket.getDestConnId(buffer);
        } else if (ShortHeaderPacket.is(type)){
            return ShortHeaderPacket.getDestConnId(buffer);
        }
        throw new Exception("Error: unknown packet (LongHeaderPacket nor ShortHeaderPacket)");
    }

    static private byte getTypeWithoutIncrement(ByteBuffer buffer) {
        int pos = buffer.position();
        byte type = buffer.get();
        buffer.position(pos);
        return type;
    }

    private final Connection conn;
    private final RoleKeys roleKeys;

    public QuicPacketParser(Connection conn, RoleKeys roleKeys) {
        this.conn = conn;
        this.roleKeys = roleKeys;
    }

    public void parseOnePacket(DatagramPacket udpPacket) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(udpPacket.getData());
        while (buffer.hasRemaining()) {
            this.parse(buffer).ifPresent(packet -> {
                //if (this.conn.getRole() == Constants.Role.SERVER) {
                //    PacketProxyUtility.getInstance().packetProxyLog("[QUIC] CLIENT--->       " + packet);
                //} else {
                //    PacketProxyUtility.getInstance().packetProxyLog("[QUIC]       <---SERVER " + packet);
                //}
                this.conn.getPnSpaces().receivePacket(packet);
            });
        }
    }

    private Optional<QuicPacket> parse(ByteBuffer buffer) throws Exception {

        byte type = getTypeWithoutIncrement(buffer);

        if (InitialPacket.is(type) && this.roleKeys.hasInitialKey()) {
            PacketNumber largestAckedPn = this.conn.getPnSpace(PnSpaceInitial).getAckFrameGenerator().getLargestAckedPn();
            InitialPacket initialPacket = new InitialPacket(buffer, this.roleKeys.getInitialKey(), largestAckedPn);
            return Optional.of(initialPacket);

        } else if (HandshakePacket.is(type) && this.roleKeys.hasHandshakeKey()) {
            PacketNumber largestAckedPn = this.conn.getPnSpace(PnSpaceHandshake).getAckFrameGenerator().getLargestAckedPn();
            HandshakePacket handshakePacket = new HandshakePacket(buffer, this.roleKeys.getHandshakeKey(), largestAckedPn);
            return Optional.of(handshakePacket);

        } else if (ShortHeaderPacket.is(type) && this.roleKeys.hasApplicationKey()) {
            PacketNumber largestAckedPn = this.conn.getPnSpace(PnSpaceApplicationData).getAckFrameGenerator().getLargestAckedPn();
            ShortHeaderPacket shortHeaderPacket = new ShortHeaderPacket(buffer, this.roleKeys.getApplicationKey(), largestAckedPn);
            return Optional.of(shortHeaderPacket);

        } else if (ZeroRttPacket.is(type) && this.roleKeys.hasZeroRttKey()) {
            PacketNumber largestAckedPn = this.conn.getPnSpace(PnSpaceApplicationData).getAckFrameGenerator().getLargestAckedPn();
            ZeroRttPacket zeroRttPacket = new ZeroRttPacket(buffer, this.roleKeys.getZeroRttKey(), largestAckedPn);
            return Optional.of(zeroRttPacket);

        } else if (type == 0x0) {
            /* remaining data in the packet is paddings */
            buffer.position(buffer.limit());
            return Optional.empty();

        } else {
            if (InitialPacket.is(type)) {
                throw new AwaitingException("InitialPacket has been received, but initial key was not found");
            }
            if (HandshakePacket.is(type)) {
                throw new AwaitingException("wait until deploying handshake key");
            }
            if (ShortHeaderPacket.is(type) || ZeroRttPacket.is(type)) {
                throw new AwaitingException("wait until deploying application key");
            }
            throw new Exception(String.format("Unknown Error: packet type (%x) received", type));
        }
    }

}
