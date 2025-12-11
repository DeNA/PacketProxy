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
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.value.SimpleBytes;

@Value
public class RawFrame implements Frame {

	public static final long TYPE = 0x123456;

	public static List<Long> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	public static RawFrame of(byte[] bytes) {
		return new RawFrame(bytes);
	}

	public static RawFrame parse(byte[] bytes) {
		return of(bytes);
	}

	public static RawFrame parse(ByteBuffer buffer) {
		byte[] frameData = SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
		return new RawFrame(frameData);
	}

	long type;
	byte[] data;

	public RawFrame(byte[] frameData) {
		this.type = TYPE;
		this.data = frameData;
	}

	@Override
	public byte[] getBytes() throws Exception {
		return this.data;
	}

	@Override
	public String toString() {
		return String.format("RawFrame(data=[%s])", Hex.encodeHexString(this.data));
	}
}
