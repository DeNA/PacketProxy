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
import lombok.Value;

@Value
public class SimpleBytes {

	public static SimpleBytes parse(ByteBuffer buffer, long sizeOfBytes) {
		byte[] bytes = new byte[(int) sizeOfBytes];
		buffer.get(bytes);
		return new SimpleBytes(bytes);
	}

	public static SimpleBytes parse(ByteBuffer buffer, int sizeOfBytes) {
		byte[] bytes = new byte[sizeOfBytes];
		buffer.get(bytes);
		return new SimpleBytes(bytes);
	}

	byte[] bytes;

	public ByteBuffer toByteBuffer() {
		return ByteBuffer.wrap(bytes);
	}
}
