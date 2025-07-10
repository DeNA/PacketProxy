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
import packetproxy.quic.value.VariableLengthInteger;

@Value
@EqualsAndHashCode(callSuper = true)
public class StreamsBlockedUniFrame extends Frame {

	public static final byte TYPE = 0x17;

	public static List<Byte> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	long maxStreams;

	public static StreamsBlockedUniFrame parse(byte[] bytes) {
		return StreamsBlockedUniFrame.parse(ByteBuffer.wrap(bytes));
	}

	public static StreamsBlockedUniFrame parse(ByteBuffer buffer) {
		byte type = buffer.get();
		assert (type == TYPE);
		long maxStreams = VariableLengthInteger.parse(buffer).getValue();
		return new StreamsBlockedUniFrame(maxStreams);
	}

	@Override
	public byte[] getBytes() {
		return new byte[]{TYPE};
	}

	@Override
	public boolean isAckEliciting() {
		return true;
	}

}
