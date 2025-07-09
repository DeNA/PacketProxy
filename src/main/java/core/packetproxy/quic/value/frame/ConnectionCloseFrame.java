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

@Value
@EqualsAndHashCode(callSuper = true)
public class ConnectionCloseFrame extends Frame {

	public static List<Byte> supportedTypes() {
		return ImmutableList.of((byte) 0x1c, (byte) 0x1d);
	}

	public static ConnectionCloseFrame parse(byte[] bytes) {
		return ConnectionCloseFrame.parse(ByteBuffer.wrap(bytes));
	}

	public static ConnectionCloseFrame parse(ByteBuffer buffer) {
		byte type = buffer.get();
		assert (supportedTypes().stream().anyMatch(t -> t == type));
		long errorCode = VariableLengthInteger.parse(buffer).getValue();
		long frameType = (type == (byte) 0x1c) ? VariableLengthInteger.parse(buffer).getValue() : 0;
		long reasonPhraseLength = VariableLengthInteger.parse(buffer).getValue();
		byte[] reasonPhrase = SimpleBytes.parse(buffer, reasonPhraseLength).getBytes();
		return new ConnectionCloseFrame(type, errorCode, frameType, reasonPhrase);
	}

	byte type;
	long errorCode;
	long frameType;
	byte[] reasonPhrase;

	@Override
	public byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(type);
		buffer.putLong(errorCode);
		if (type == (byte) 0x1c) {
			buffer.putLong(frameType);
		}
		buffer.put(VariableLengthInteger.of(reasonPhrase.length).getBytes());
		buffer.put(reasonPhrase);
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	@Override
	public boolean isAckEliciting() {
		return false;
	}

	public String getReasonPhraseString() {
		return this.reasonPhrase != null ? new String(reasonPhrase) : "";
	}

	@Override
	public String toString() {
		return String.format("ConnectionCloseFrame(errorCode=%d, frameType=%d, reason=%s)", this.errorCode,
				this.frameType, new String(this.reasonPhrase));
	}
}
