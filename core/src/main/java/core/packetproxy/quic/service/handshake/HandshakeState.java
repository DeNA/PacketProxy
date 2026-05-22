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

package packetproxy.quic.service.handshake;

public class HandshakeState {

	public enum State {
		Initial, HasHandshakeKeys, AckReceived, HasAppKeys, Confirmed
	}

	private State state;

	public HandshakeState() {
		this.state = State.Initial;
	}

	public void transit(HandshakeState.State newlyState) {
		this.state = newlyState;
	}

	public boolean hasNoHandshakeKeys() {
		return this.state.ordinal() < State.HasHandshakeKeys.ordinal();
	}

	public boolean hasHandshakeKeys() {
		return this.state.ordinal() >= State.HasHandshakeKeys.ordinal();
	}

	public boolean isAckReceived() {
		return this.state.ordinal() >= State.AckReceived.ordinal();
	}

	public boolean isNotConfirmed() {
		return this.state.ordinal() < State.Confirmed.ordinal();
	}

	public boolean isConfirmed() {
		return this.state.ordinal() >= State.Confirmed.ordinal();
	}
}
