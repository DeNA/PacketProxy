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


import org.apache.commons.lang3.tuple.ImmutablePair;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.utils.Constants.*;
import packetproxy.quic.service.connection.Connection;

import java.time.Instant;

import static packetproxy.quic.utils.Constants.PnSpaceType.*;


public class Pto {

    private final Connection conn;
    private long ptoCount = 0;

    public Pto(Connection conn) {
        this.conn = conn;
    }

    public void clearPtoCount() {
        this.ptoCount = 0;
    }

    public void incrementPtoCount() {
        this.ptoCount++;
    }

    public Instant getPtoTime() {
        return this.getPtoTimeAndSpace().getLeft();
    }

    public PnSpaceType getPtoSpaceType() {
        return this.getPtoTimeAndSpace().getRight();
    }

    public ImmutablePair<Instant, PnSpaceType> getPtoTimeAndSpace() {
        long smoothedRtt = this.conn.getRttEstimator().getSmoothedRtt();
        long rttVar = this.conn.getRttEstimator().getRttVar();

        long duration = (smoothedRtt + Math.max(4 * rttVar, Constants.kGranularity)) * (long)(Math.pow(2, this.ptoCount));

        // Anti-deadlock PTO starts from the current time
        if (this.conn.peerAwaitingAddressValidation()) {
            if (this.conn.getHandshakeState().hasNoHandshakeKeys()) {
                return ImmutablePair.of(
                        Instant.now().plusMillis(duration),
                        PnSpaceInitial);
            } else {
                return ImmutablePair.of(
                        Instant.now().plusMillis(duration),
                        PnSpaceHandshake);
            }
        }

        Instant ptoTimeout = Instant.MAX;
        PnSpaceType ptoPnSpaceType = PnSpaceInitial;

        for (PnSpaceType pnSpaceType : PnSpaceType.values()) {
            if (!this.conn.getPnSpace(pnSpaceType).hasAnyAckElicitingPacket()) {
                continue;
            }
            if (pnSpaceType == PnSpaceApplicationData) {
                // Skip Application Data until handshake confirmed.
                if (this.conn.getHandshakeState().isNotConfirmed()) {
                    return ImmutablePair.of(ptoTimeout, ptoPnSpaceType);
                }
                // Include max_ack_delay and backoff for Application Data.
                duration += this.conn.getRttEstimator().getMaxAckDelay() * (long)(Math.pow(2, this.ptoCount));
            }
            Instant t = this.conn.getPnSpace(pnSpaceType).getTimeOfLastAckElicitingPacket().plusMillis(duration);
            if (t.isBefore(ptoTimeout)) {
                ptoTimeout = t;
                ptoPnSpaceType = pnSpaceType;
            }
        }
        return ImmutablePair.of(ptoTimeout, ptoPnSpaceType);
    }

}
