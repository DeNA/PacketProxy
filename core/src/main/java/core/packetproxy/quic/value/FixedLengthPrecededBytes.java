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

package packetproxy.quic.value;

import java.nio.ByteBuffer;
import lombok.Getter;

@Getter
public class FixedLengthPrecededBytes {

	public static FixedLengthPrecededBytes of(byte[] bytes) {
		return new FixedLengthPrecededBytes(bytes);
	}

	public static FixedLengthPrecededBytes parse(ByteBuffer buffer) {
		byte length = buffer.get();
		byte[] bytes = new byte[length];
		buffer.get(bytes);
		return new FixedLengthPrecededBytes(bytes);
	}

	private byte[] bytes;

	private FixedLengthPrecededBytes(byte[] bytes) {
		this.bytes = bytes;
	}

	public byte[] serialize() {
		ByteBuffer buffer = ByteBuffer.allocate(255);
		buffer.put((byte) bytes.length);
		buffer.put(bytes);
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}
}
