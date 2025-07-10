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
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

@Value
public class HeadersFrame implements Frame {

	public static final long TYPE = 0x01;

	public static List<Long> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	public static HeadersFrame parse(ByteBuffer buffer) {
		long frameType = VariableLengthInteger.parse(buffer).getValue();
		long frameLength = VariableLengthInteger.parse(buffer).getValue();
		byte[] frameData = SimpleBytes.parse(buffer, frameLength).getBytes();
		return new HeadersFrame(frameData);
	}

	public static HeadersFrame of(byte[] frameData) {
		return new HeadersFrame(frameData);
	}

	long type;
	byte[] data;

	private HeadersFrame(byte[] frameData) {
		this.type = TYPE;
		this.data = frameData;
	}

	public byte[] getData() {
		return this.data;
	}

	@Override
	public byte[] getBytes() {
		ByteArrayOutputStream headersFrameStream = new ByteArrayOutputStream();
		try {

			headersFrameStream.write(VariableLengthInteger.of(this.type).getBytes());
			headersFrameStream.write(VariableLengthInteger.of(this.data.length).getBytes());
			headersFrameStream.write(this.data);
		} catch (Exception e) {

			e.printStackTrace();
		}
		return headersFrameStream.toByteArray();
	}

	@Override
	public String toString() {
		return String.format("HeadersFrame(data=[%s])", Hex.encodeHexString(this.data));
	}
}
