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
public class MaxDataFrame extends Frame {

	public static final byte TYPE = 0x10;
	long maxData;

	public static List<Byte> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	public static MaxDataFrame parse(byte[] bytes) {
		return MaxDataFrame.parse(ByteBuffer.wrap(bytes));
	}

	public static MaxDataFrame parse(ByteBuffer buffer) {
		byte type = buffer.get();
		long maxData = VariableLengthInteger.parse(buffer).getValue();
		return new MaxDataFrame(maxData);
	}

	@Override
	public byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(TYPE);
		buffer.put(VariableLengthInteger.of(this.maxData).getBytes());
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	@Override
	public boolean isAckEliciting() {
		return true;
	}

}
