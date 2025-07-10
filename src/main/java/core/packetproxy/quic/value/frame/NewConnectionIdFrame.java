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

package packetproxy.quic.value.frame;

import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.FixedLengthPrecededBytes;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.Token;
import packetproxy.quic.value.VariableLengthInteger;

/*
https://www.rfc-editor.org/rfc/rfc9000.html#section-19.15

NEW_CONNECTION_ID Frame {
  Type (i) = 0x18,
  Sequence Number (i),
  Retire Prior To (i),
  Length (8),
  Connection ID (8..160),
  Stateless Reset Token (128),
}
*/
@Value
@EqualsAndHashCode(callSuper = true)
public class NewConnectionIdFrame extends Frame {

	public static final byte TYPE = 0x18;

	public static List<Byte> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	long sequenceNumber;
	long retirePriorTo;
	ConnectionId connectionId;
	Token stateResetToken;

	public static NewConnectionIdFrame parse(byte[] bytes) {
		return NewConnectionIdFrame.parse(ByteBuffer.wrap(bytes));
	}

	public static NewConnectionIdFrame parse(ByteBuffer buffer) {
		byte type = buffer.get();
		assert (type == TYPE);
		long sequenceNumber = VariableLengthInteger.parse(buffer).getValue();
		long retirePriorTo = VariableLengthInteger.parse(buffer).getValue();
		ConnectionId connectionId = ConnectionId.of(FixedLengthPrecededBytes.parse(buffer).getBytes());
		Token statelessResetToken = Token.of(SimpleBytes.parse(buffer, 16).getBytes());

		return new NewConnectionIdFrame(sequenceNumber, retirePriorTo, connectionId, statelessResetToken);
	}

	@Override
	public byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(TYPE);
		buffer.put(VariableLengthInteger.of(this.sequenceNumber).getBytes());
		buffer.put(VariableLengthInteger.of(this.retirePriorTo).getBytes());
		buffer.put(FixedLengthPrecededBytes.of(this.connectionId.getBytes()).serialize());
		buffer.put(this.stateResetToken.getBytes());
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	@Override
	public boolean isAckEliciting() {
		return true;
	}

}
