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

/*
https://datatracker.ietf.org/doc/html/draft-ietf-quic-transport-20#section-16
+------+--------+-------------+-----------------------+
| 2Bit | Length | Usable Bits | Range                 |
+------+--------+-------------+-----------------------+
| 00   | 1      | 6           | 0-63                  |
| 01   | 2      | 14          | 0-16383               |
| 10   | 4      | 30          | 0-1073741823          |
| 11   | 8      | 62          | 0-4611686018427387903 |
+------+--------+-------------+-----------------------+
*/
@Value
public class VariableLengthInteger {

	public static VariableLengthInteger of(long value) {
		return new VariableLengthInteger(value);
	}

	public static VariableLengthInteger parse(byte[] bytes) {
		int len = bytes.length;
		long val = 0;
		for (int i = 0; i < len; i++) {
			if (i == 0) {
				val = bytes[0] & 0x3f;
			} else {
				val = (val << 8) | (((long) bytes[i]) & 0xff);
			}
		}
		return new VariableLengthInteger(val);
	}

	public static VariableLengthInteger parse(ByteBuffer buffer) {
		byte byte0 = buffer.get();
		int length = estimateLength(byte0);
		buffer.position(buffer.position() - 1);
		return parse(SimpleBytes.parse(buffer, length).getBytes());
	}

	private static int estimateLength(byte byte0) {
		switch (byte0 & 0xc0) {
			case 0x00 :
				return 1;
			case 0x40 :
				return 2;
			case 0x80 :
				return 4;
			case 0xc0 :
				return 8;
			default :
				return -1; // never reach here
		}
	}

	private static int estimateLength(long value) {
		assert (0 <= value && value < 0x4000000000000000L);
		if (value < 0x40L) {
			return 1;
		} else if (value < 0x4000L) {
			return 2;
		} else if (value < 0x40000000L) {
			return 4;
		} else {
			return 8;
		}
	}

	long value;

	public byte[] getBytes() {
		int len = estimateLength(this.value);
		ByteBuffer buf = ByteBuffer.allocate(len);
		switch (len) {
			case 1 :
				buf.put((byte) this.value);
				break;
			case 2 :
				buf.putShort((short) (((short) this.value) | 0x4000));
				break;
			case 4 :
				buf.putInt(((int) this.value) | 0x80000000);
				break;
			case 8 :
				buf.putLong(this.value | 0xC000000000000000L);
				break;
		}
		return buf.array();
	}

}
