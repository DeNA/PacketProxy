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
import java.util.Arrays;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class PingFrame extends Frame {

	public static final byte TYPE = 0x01;
	long length;

	public static List<Byte> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	public static PingFrame parse(byte[] bytes) {
		return PingFrame.parse(ByteBuffer.wrap(bytes));
	}

	public static PingFrame parse(ByteBuffer buffer) {
		long length = 0;
		while (buffer.remaining() > 0) {
			byte type = buffer.get();
			if (type != TYPE) {
				buffer.position(buffer.position() - 1);
				break;
			}
			length++;
		}
		return new PingFrame(length);
	}

	public static PingFrame generate() {
		return new PingFrame(1);
	}

	@Override
	public byte[] getBytes() {
		byte[] bytes = new byte[(int) this.length];
		Arrays.fill(bytes, TYPE);
		return bytes;
	}

	@Override
	public boolean isAckEliciting() {
		return true;
	}

}
