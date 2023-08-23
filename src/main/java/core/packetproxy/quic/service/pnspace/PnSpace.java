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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import packetproxy.quic.service.connection.Connection;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.service.frame.FramesBuilder;
import packetproxy.quic.service.framegenerator.AckFrameGenerator;
import packetproxy.quic.service.framegenerator.CryptoFramesToMessages;
import packetproxy.quic.service.framegenerator.MessagesToCryptoFrames;
import packetproxy.quic.service.framegenerator.StreamFramesToMessages;
import packetproxy.quic.service.packet.QuicPacketBuilder;
import packetproxy.quic.service.pnspace.helper.LostPackets;
import packetproxy.quic.service.pnspace.helper.SendFrameQueue;
import packetproxy.quic.service.pnspace.helper.SentPackets;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.SentPacket;
import packetproxy.quic.value.frame.*;
import packetproxy.quic.value.packet.PnSpacePacket;
import packetproxy.quic.value.packet.QuicPacket;
import packetproxy.util.PacketProxyUtility;

import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static packetproxy.quic.service.handshake.HandshakeState.State.Confirmed;
import static packetproxy.quic.utils.Constants.*;
import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceHandshake;
import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceInitial;
import static packetproxy.util.Throwing.rethrow;

@Getter
@NoArgsConstructor(access = AccessLevel.NONE)
public abstract class PnSpace {

    protected final CryptoFramesToMessages frameToMsgCryptoStream = new CryptoFramesToMessages();
    protected final MessagesToCryptoFrames msgToFrameCryptoStream = new MessagesToCryptoFrames();
    protected final StreamFramesToMessages frameToMsgStream = new StreamFramesToMessages();

    protected final AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();
    protected final SentPackets sentPackets = new SentPackets();
    protected final SendFrameQueue sendFrameQueue = new SendFrameQueue();
    protected final Connection conn;
    protected PacketNumber largestAckedPn = PacketNumber.Infinite;
    protected PacketNumber largestAckedPnrInitiatedByPeer = PacketNumber.Infinite;
    protected Instant lossTime = Instant.MAX; /* パケットが欠落することになる未来時刻 */
    protected Instant timeOfLastAckElicitingPacket = Instant.MIN; /* 最後にAckを誘発するPacketを送信した時刻 */
    protected PacketNumber nextPacketNumber = PacketNumber.of(0);
    protected PnSpaceType pnSpaceType;

    public PnSpace(Connection conn, PnSpaceType pnSpaceType) {
        this.conn = conn;
        this.pnSpaceType = pnSpaceType;
    }

    public PacketNumber getNextPacketNumberAndIncrement() {
        PacketNumber pn = PacketNumber.copy(this.nextPacketNumber);
        this.nextPacketNumber = this.nextPacketNumber.plus(1);
        return pn;
    }

    public synchronized boolean hasAnyAckElicitingPacket() {
        return this.sentPackets.hasAnyAckElicitingPacket();
    }

    public synchronized void close() {
        this.sentPackets.clear();
        this.sendFrameQueue.clear();
        this.lossTime = Instant.MAX;
        this.timeOfLastAckElicitingPacket = Instant.MIN;
    }

    public synchronized void OnAckReceived(PacketNumber pn, AckFrame ackFrame) {
        if (this.largestAckedPn == PacketNumber.Infinite) {
            this.largestAckedPn = ackFrame.getLargestAckedPn();
        } else {
            this.largestAckedPn = PacketNumber.max(this.largestAckedPn, ackFrame.getLargestAckedPn());
        }
        SentPackets newlyAckedPackets = sentPackets.detectAndRemoveAckedPackets(ackFrame);
        if (newlyAckedPackets.isEmpty()) {
            return;
        }

        /* Update the RTT if the largest acknowledged is newly acked, and at least one ack-eliciting was newly acked. */
        newlyAckedPackets.getLargest().ifPresent(packet -> {
            if (packet.getPacketNumber() == ackFrame.getLargestAckedPn() && newlyAckedPackets.hasAnyAckElicitingPacket()) {
                this.conn.getRttEstimator().updateRtt(packet.getTimeSent(), ackFrame.getAckDelay());
            }
        });

        if (ackFrame instanceof AckEcnFrame) {
            /* PacketProxyでは通信路の輻輳制御機能は実装しない */
            // ProcessECN(ackFrame, pn_space);
        }

        LostPackets lostPackets = this.detectAndRemoveLostPackets();
        if (!lostPackets.isEmpty()) {
            //PacketProxyUtility.getInstance().packetProxyLogErr("[QUIC] lost packets: " + lostPackets);
            OnPacketsLost(lostPackets);
        }

        /* Reset pto_count unless the client is unsure if the server has validated the client's address. */
        if (this.conn.peerCompletedAddressValidation()) {
            this.conn.getPto().clearPtoCount();
        }
        this.conn.getLossDetection().setLossDetectionTimer();
    }

    public synchronized void receivePacket(QuicPacket quicPacket) {
        if (quicPacket instanceof PnSpacePacket packet) {
            packet.getAckFrame().ifPresent(ackFrame -> {
                ackFrame.getAckedPacketNumbers().stream().forEach(pn -> {
                    SentPacket sp = this.sentPackets.get(pn);
                    if (sp != null) {
                        PnSpacePacket pnPacket = sp.getPacket();
                        if (pnPacket != null) {
                            pnPacket.getAckFrame().ifPresent(ackFrame1 -> {
                                this.largestAckedPnrInitiatedByPeer = ackFrame1.getLargestAckedPn();
                            });
                        }
                    }
                });
            });
            this.ackFrameGenerator.received(packet.getPacketNumber());
            if (packet.isAckEliciting()) {
                this.addSendFrameFirst(this.ackFrameGenerator.generateAckFrame());
            }
            this.receiveFrames(packet.getPacketNumber(), packet.getFrames());
        }
    }

    private synchronized void receiveFrames(PacketNumber pn, Frames frames) {
        for (Frame frame : frames) {
            if (frame instanceof CryptoFrame) {
                frameToMsgCryptoStream.write((CryptoFrame) frame);
                frameToMsgCryptoStream.getHandshakeMessages().forEach(rethrow(msg -> {
                    this.conn.getHandshake().received(msg);
                }));
            } else if (frame instanceof StreamFrame) {
                StreamFrame streamFrame = (StreamFrame) frame;
                this.frameToMsgStream.put(streamFrame);
                this.frameToMsgStream.get(streamFrame.getStreamId()).ifPresent(rethrow(msg -> {
                    OutputStream os = this.conn.getPipe().getRawEndpoint().getOutputStream();
                    os.write(msg.getBytes());
                    os.flush();
                }));
            } else if (frame instanceof AckFrame) {
                this.OnAckReceived(pn, (AckFrame) frame);
            } else if (frame instanceof HandshakeDoneFrame) {
                this.conn.getHandshakeState().transit(Confirmed);
                if (this.conn.getRole() == Constants.Role.CLIENT) {
                    this.conn.getKeys().discardInitialKey();
                    this.conn.getPnSpace(PnSpaceInitial).close();
                    this.conn.getKeys().discardHandshakeKey();
                    this.conn.getPnSpace(PnSpaceHandshake).close();
                }
            } else if (frame instanceof ConnectionCloseFrame) {
                this.conn.close();
            } else if (frame instanceof NewConnectionIdFrame) {
                /* not implemented yet */
            } else if (frame instanceof NewTokenFrame) {
                /* not implemented yet */
            } else if (frame instanceof PingFrame) {
                /* Do Nothing */
            } else if (frame instanceof PaddingFrame) {
                /* Do Nothing */
            } else if (frame instanceof MaxDataFrame) {
                /* Do Nothing */
            } else if (frame instanceof StopSendingFrame) {
                /* Do Nothing */
            } else if (frame instanceof ResetStreamFrame) {
                this.conn.close();
            } else {
                System.err.println("Error: cannot process frame: " + frame);
            }
        }
    }

    public synchronized void addSendQuicMessage(QuicMessage msg) {
        /* defined on ApplicationData PnSpace only */
    }

    public synchronized void addSendFrameFirst(Frame frame) {
        QuicPacketBuilder builder = QuicPacketBuilder.getBuilder()
                .setPnSpaceType(this.pnSpaceType)
                .setFramesBuilder(new FramesBuilder().add(frame));
        this.conn.getPnSpaces().addSendPacketsFirst(builder);
    }

    public void addSendFrame(Frame frame) {
        this.addSendFrames(Frames.of(frame));
    }

    public synchronized void addSendFrames(Frames frames) {
        this.sendFrameQueue.add(frames);
        this.conn.getPnSpaces().addSendPackets(this.getAndRemoveSendFramesAndConvertPacketBuilders());
    }
    public abstract List<QuicPacketBuilder> getAndRemoveSendFramesAndConvertPacketBuilders();

    public synchronized void addSentPacket(QuicPacket quicPacket) {
        if (quicPacket instanceof PnSpacePacket packet) {
            this.sentPackets.add(new SentPacket(packet));
            if (packet.isAckEliciting()) {
                this.timeOfLastAckElicitingPacket = Instant.now();
            }
        }
    }

    public void OnPacketsLost(LostPackets lostPackets) {
        lostPackets.stream().forEach(sentPacket -> {
            this.addSendFrames(sentPacket.getPacket().getFrames()); /* resend packet */
        });
    }

    public synchronized LostPackets detectAndRemoveLostPackets() {
        assert (this.largestAckedPn != PacketNumber.Infinite);
        this.lossTime = Instant.MAX;

        LostPackets lostPackets = new LostPackets();
        long lossDelay = this.conn.getRttEstimator().getLossDelay();

        /* Packets sent before this time are deemed lost. */
        Instant lostSendTime = Instant.now().minusMillis(lossDelay);

        for (SentPacket unAcked : this.sentPackets.getUnAckedPackets()) {
            PacketNumber largestAckedPn = this.largestAckedPn;
            PacketNumber unAckedPn = unAcked.getPacketNumber();
            if (unAckedPn.isLargerThan(largestAckedPn)) {
                continue;
            }
            /*
             * Mark packet as lost, or set time when it should be marked.
             * Note: The use of kPacketThreshold here assumes that there were no sender-induced gaps in the packet number space.
             */
            if (unAcked.getTimeSent().isBefore(lostSendTime) || largestAckedPn.isLargerThanOrEquals(unAckedPn.plus(Constants.kPacketThreshold))) {
                this.sentPackets.removePacket(unAcked);
                lostPackets.add(unAcked);
            } else {
                Instant newlyLossTime = unAcked.getTimeSent().plusMillis(lossDelay);
                if (newlyLossTime.isBefore(this.lossTime)) {
                    this.lossTime = newlyLossTime;
                }
            }
        }
        return lostPackets;
    }

}
