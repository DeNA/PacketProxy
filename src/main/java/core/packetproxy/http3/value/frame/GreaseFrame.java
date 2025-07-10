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

package packetproxy.http3.value.frame;

import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;
import lombok.Value;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

@Value
public class GreaseFrame implements Frame {

	static final long GreaseType = 0xDEADBEEFL;

	public static List<Long> supportedTypes() {
		return ImmutableList.of(GreaseType);
	}

	public static GreaseFrame of(long type, byte[] bytes) {
		return new GreaseFrame(type, bytes);
	}

	public static GreaseFrame parse(long type, byte[] bytes) {
		return of(type, bytes);
	}

	public static GreaseFrame parse(ByteBuffer buffer) {
		long frameType = VariableLengthInteger.parse(buffer).getValue();
		int startOfLength = buffer.position();
		long frameLength = VariableLengthInteger.parse(buffer).getValue();
		byte[] frameData = new byte[]{};
		if (frameLength > buffer.remaining()) {

			buffer.position(startOfLength);
			frameData = SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
		} else {

			frameData = SimpleBytes.parse(buffer, frameLength).getBytes();
		}
		return new GreaseFrame(frameType, frameData);
	}

	long type;
	byte[] data;

	public GreaseFrame(long frameType, byte[] frameData) {
		this.type = frameType;
		this.data = frameData;
	}

	@Override
	public byte[] getBytes() throws Exception {
		return this.data;
	}

	@Override
	public String toString() {
		return String.format("GreaseFrame(type=0x%x,data=[%s])", this.type, new String(this.data));
	}

}
