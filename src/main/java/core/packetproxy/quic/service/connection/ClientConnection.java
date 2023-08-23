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

package packetproxy.quic.service.connection;

import lombok.Getter;
import packetproxy.common.Endpoint;
import packetproxy.model.CAs.CA;
import packetproxy.quic.service.handshake.ServerHandshake;
import packetproxy.quic.utils.AwaitingException;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.util.PacketProxyUtility;

import javax.crypto.AEADBadTagException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

@Getter
public class ClientConnection extends Connection implements Endpoint {

    private final int listenPortNum;

    private final ServerHandshake handshake;

    public ClientConnection(ConnectionIdPair connIdPair, ConnectionId initialSecret, DatagramSocket socket, InetSocketAddress peerAddr, CA ca, int listenPortNum) throws Exception {
        super(Constants.Role.SERVER, connIdPair, initialSecret, socket, peerAddr);
        this.listenPortNum = listenPortNum;
        this.handshake = new ServerHandshake(this, ca);
        super.start();
    }

    /**
     * ClientHello内部のSNIフィールドを取得する (Blocking)
     */
    public String getSNI() throws Exception {
        return this.handshake.getSNI();
    }

    /**
     *  クライアントからの受信パケット -> 処理 -> パケットキュー
     */
    public void recvUdpPacket(DatagramPacket udpPacket) {
        awaitingReceivedPackets.put(udpPacket);
        awaitingReceivedPackets.forEachAndRemovedIfReturnTrue(packet -> {
            try {
                clientPacketParser.parseOnePacket(packet);
                return true; /* 正常終了したので、受信パケットリストから捨てる */
            } catch (AwaitingException e) {
                return false; /* 少し待ってから、もう一度処理 */
            } catch (java.io.IOException e) {
                return true; /* 処理できないので捨てる */
            } catch (AEADBadTagException e) {
                e.printStackTrace();
                return true; /* 処理できないので捨てる */
            } catch (Exception e) {
                e.printStackTrace();
                return true; /* 処理できないので捨てる */
            }
        });
    }

    @Override
    public boolean peerCompletedAddressValidation() {
        return true;
    }

    @Override
    public int getLocalPort() {
        return this.listenPortNum;
    }

    @Override
    public String getName() {
        return "QUIC Client Endpoint";
    }
}
