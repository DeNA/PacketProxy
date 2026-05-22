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
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

/*
https://www.rfc-editor.org/rfc/rfc9000.html#section-19.4

RESET_STREAM Frame {
  Type (i) = 0x04,
  Stream ID (i),
  Application Protocol Error Code (i),
  Final Size (i),
}
*/
@Value
@EqualsAndHashCode(callSuper = true)
public class ResetStreamFrame extends Frame {

	public static final byte TYPE = 0x04;

	public static List<Byte> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	public static ResetStreamFrame parse(byte[] bytes) {
		return ResetStreamFrame.parse(ByteBuffer.wrap(bytes));
	}

	public static ResetStreamFrame parse(ByteBuffer buffer) {
		byte type = buffer.get();
		assert (type == TYPE);
		long streamId = VariableLengthInteger.parse(buffer).getValue();
		long applicationProtocolErrorCode = VariableLengthInteger.parse(buffer).getValue();
		long finalSize = VariableLengthInteger.parse(buffer).getValue();
		return new ResetStreamFrame(streamId, applicationProtocolErrorCode, finalSize);
	}

	long streamId;
	long applicationErrorCode;
	long finalSize;

	@Override
	public byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(TYPE);
		buffer.put(VariableLengthInteger.of(this.streamId).getBytes());
		buffer.put(VariableLengthInteger.of(this.applicationErrorCode).getBytes());
		buffer.put(VariableLengthInteger.of(this.finalSize).getBytes());
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	@Override
	public boolean isAckEliciting() {
		return true;
	}
}
