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

import static java.lang.Math.min;

import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import packetproxy.quic.service.connection.Connection;
import packetproxy.quic.utils.Constants;

@Getter
public class RttEstimator {

	private final Connection conn;
	private long initialRtt = 333;
	private Instant firstRttSample = Instant.MIN;
	private long minRtt = 0;
	private long smoothedRtt = initialRtt;
	private long rttVar = initialRtt / 2;
	private long latestRtt = 0;
	private long maxAckDelay = 25; /* a default of 25 milliseconds is assumed (rfc9000) */

	public RttEstimator(Connection conn) {
		this.conn = conn;
	}

	/** ref: https://www.rfc-editor.org/rfc/rfc9002.html#section-a.7 */
	public void updateRtt(Instant timeSent, long ackDelay) {

		Instant timeReceived = Instant.now();
		this.latestRtt = Duration.between(timeReceived, timeSent).toMillis();

		if (firstRttSample == Instant.MIN) {

			this.minRtt = this.latestRtt;
			this.smoothedRtt = this.latestRtt;
			this.rttVar = this.latestRtt / 2;
			this.firstRttSample = timeReceived;
		}

		// min_rtt ignores acknowledgment delay.
		this.minRtt = min(this.minRtt, this.latestRtt);

		// Limit ack_delay by max_ack_delay after handshake confirmation.
		if (conn.getHandshakeState().isConfirmed()) {

			ackDelay = min(ackDelay, this.maxAckDelay);
		}

		// Adjust for acknowledgment delay if plausible.
		long adjustedRtt = this.latestRtt;
		if (this.latestRtt >= this.minRtt + ackDelay) {

			adjustedRtt = this.latestRtt - ackDelay;
		}

		long currentRttVar = Math.abs(this.smoothedRtt - adjustedRtt);
		this.rttVar = (3 * this.rttVar + currentRttVar) / 4;
		this.smoothedRtt = (7 * this.smoothedRtt + adjustedRtt) / 8;
	}

	public long getLossDelay() {
		// Minimum time of kGranularity before packets are deemed lost.
		return (long) (Constants.kTimeThreshold * Math.max(this.latestRtt, this.smoothedRtt));
	}
}
