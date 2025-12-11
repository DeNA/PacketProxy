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

package packetproxy.quic.service;

import static packetproxy.quic.utils.Constants.PnSpaceType;
import static packetproxy.quic.utils.Constants.PnSpaceType.*;

import java.time.Instant;
import packetproxy.quic.service.connection.Connection;
import packetproxy.quic.service.pnspace.helper.LostPackets;
import packetproxy.quic.utils.ScheduledTimer;
import packetproxy.quic.value.frame.PingFrame;

public class LossDetection {

	private final Connection conn;
	private final ScheduledTimer scheduledTimer;

	public LossDetection(Connection conn) {
		this.conn = conn;
		this.scheduledTimer = new ScheduledTimer(this::onLossDetectionTimeout);
	}

	public synchronized void setLossDetectionTimer() {
		Instant earliestLossTime = this.conn.getPnSpaces().getEarliestLossTime();
		if (earliestLossTime != Instant.MAX) {

			/*
			 * Time threshold loss detection.
			 * パケットロスが発生する時間にタイマーをセット
			 */
			scheduledTimer.update(earliestLossTime);
			return;
		}
		if (this.conn.isServerAntiAmplifiedLimit()) {

			/*
			 * The server's timer is not set if nothing can be sent.
			 * anti-amplification状態のときは、タイマーはセットしない。
			 * ただ、PacketProxyでは、anti-amplificationは未実装なのでここに入らない。
			 */
			scheduledTimer.cancel();
			return;
		}
		if (!this.conn.getPnSpaces().hasAnyAckElicitingPacket() && this.conn.peerCompletedAddressValidation()) {

			/*
			 * There is nothing to detect lost, so no timer is set.
			 * However, the client needs to arm the timer if the
			 * server might be blocked by the anti-amplification limit.
			 * 何もパケットを送信していないし、anti-amplificationも解除されているので、タイマーをセットする必要がない
			 */
			scheduledTimer.cancel();
			return;
		}
		/*
		 * 最後にAckElicitingパケットを投げた時刻から計算したAckが返ってくる制限時間にタイマーをセット
		 */
		scheduledTimer.update(conn.getPto().getPtoTime());
	}

	private synchronized void onLossDetectionTimeout() {
		// PacketProxyUtility.getInstance().packetProxyLogErr("[QUIC] LossDetection
		// Timeout!");

		var earliestLossTimeAndSpace = this.conn.getPnSpaces().getEarliestLossTimeAndSpace();
		Instant earliestLossTime = earliestLossTimeAndSpace.getLeft();
		PnSpaceType earliestPnSpaceType = earliestLossTimeAndSpace.getRight();

		if (earliestLossTime != Instant.MAX) {

			/*
			 * Time threshold loss Detection
			 * パケットロスが発生したので該当パケットを再送
			 */
			LostPackets lostPackets = this.conn.getPnSpace(earliestPnSpaceType).detectAndRemoveLostPackets();
			assert (!lostPackets.isEmpty());
			this.conn.getPnSpace(earliestPnSpaceType).OnPacketsLost(lostPackets);
			setLossDetectionTimer();
			return;
		}
		if (!this.conn.getPnSpaces().hasAnyAckElicitingPacket()) {

			if (this.conn.peerAwaitingAddressValidation()) {

				/*
				 * Client sends an anti-deadlock packet: Initial is padded to earn more anti-amplification credit,
				 * a Handshake packet proves address ownership.
				 * サーバーがanti-amplificationのため、何も送信できない状態に陥っているため、何かパケットを投げて送信できるようにする
				 */
				if (this.conn.getHandshakeState().hasHandshakeKeys()) {

					this.conn.getPnSpace(PnSpaceHandshake).addSendFrame(PingFrame.generate());
				} else {

					this.conn.getPnSpace(PnSpaceInitial).addSendFrame(PingFrame.generate());
				}
			}
		} else {

			/*
			 * PTO. Send new data if available, else retransmit old data.
			 * If neither is available, send a single PING frame.
			 * AckElicitingパケットを投げたのに、Ackが返ってこないので催促する
			 */
			PnSpaceType ptoPnSpaceType = this.conn.getPto().getPtoSpaceType();
			// PacketProxyUtility.getInstance().packetProxyLogErr(
			// String.format("[QUIC] Probe Timeout: send ack-eliciting packet (%s:%s)",
			// this.conn.getRole(),
			// ptoPnSpaceType.toString()));

			if (this.conn.getHandshakeState().isConfirmed()) {
				/* 現在は ApplicationData ステート */

				if (ptoPnSpaceType == PnSpaceApplicationData) {

					this.conn.getPnSpace(ptoPnSpaceType).addSendFrame(PingFrame.generate());
				}
			} else if (this.conn.getHandshakeState().isAckReceived()) {
				/* 現在は Handshake ステート */

				if (ptoPnSpaceType == PnSpaceHandshake) {

					this.conn.getPnSpace(ptoPnSpaceType).addSendFrame(PingFrame.generate());
				}
			} else {
				/* 現在は Initial ステート */

				// if (ptoPnSpaceType == PnSpaceInitial) {
				// this.conn.getPnSpace(ptoPnSpaceType).addSendFrame(PingFrame.generate());
				// }
			}
		}
		this.conn.getPto().incrementPtoCount();
		setLossDetectionTimer();
	}
}
