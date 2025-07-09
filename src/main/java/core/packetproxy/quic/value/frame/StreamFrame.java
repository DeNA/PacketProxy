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
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.StreamId;
import packetproxy.quic.value.VariableLengthInteger;

@EqualsAndHashCode(callSuper = true)
@Value(staticConstructor = "of")
public class StreamFrame extends Frame {

	public static boolean is(byte type) {
		return supportedTypes().stream().anyMatch(t -> t == type);
	}

	public static List<Byte> supportedTypes() {
		return ImmutableList.of((byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c, (byte) 0x0d,
				(byte) 0x0e, (byte) 0x0f);
	}

	private static boolean hasOffsetField(byte type) {
		return (type & 0x04) > 0;
	}
	private static boolean hasLengthField(byte type) {
		return (type & 0x02) > 0;
	}
	private static boolean hasFinishBit(byte type) {
		return (type & 0x01) > 0;
	}

	public static StreamFrame parse(ByteBuffer buffer) {
		byte type = buffer.get();
		assert (StreamFrame.is(type));

		StreamId streamId = StreamId.of(VariableLengthInteger.parse(buffer).getValue());
		long offset = hasOffsetField(type) ? VariableLengthInteger.parse(buffer).getValue() : 0;
		long length = hasLengthField(type) ? VariableLengthInteger.parse(buffer).getValue() : 0;
		boolean finished = hasFinishBit(type);
		byte[] streamData;
		if (length > 0) {
			streamData = SimpleBytes.parse(buffer, length).getBytes();
		} else {
			streamData = SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
			length = streamData.length;
		}
		return StreamFrame.of(streamId, offset, length, streamData, finished);
	}

	StreamId streamId;
	long offset;
	long length;
	byte[] streamData;
	boolean finished;

	@Override
	public String toString() {
		return String.format("StreamFrame(streamId=%s, type=%x, offset=%d, length=%d, data=[%s])", this.streamId,
				this.getType(), this.offset, this.length, Hex.encodeHexString(this.streamData));
	}

	private byte getType() {
		byte offsetBit = (this.offset > 0) ? (byte) 0x04 : 0x00;
		byte lengthBit = (this.length > 0) ? (byte) 0x02 : 0x00;
		byte finishBit = this.finished ? (byte) 0x01 : 0x00;
		return (byte) (0x08 | offsetBit | lengthBit | finishBit);
	}

	@Override
	public byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(this.getType());
		buffer.put(VariableLengthInteger.of(this.streamId.getId()).getBytes());
		if (this.offset > 0) {
			buffer.put(VariableLengthInteger.of(this.offset).getBytes());
		}
		if (this.length > 0) {
			buffer.put(VariableLengthInteger.of(this.length).getBytes());
		}
		buffer.put(this.streamData);
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	@Override
	public boolean isAckEliciting() {
		return true;
	}

}
