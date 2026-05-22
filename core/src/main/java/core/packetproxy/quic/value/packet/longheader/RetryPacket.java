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

package packetproxy.quic.value.packet.longheader;

import java.nio.ByteBuffer;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

/*
https://datatracker.ietf.org/doc/html/rfc9000#section-17.2.5
Retry Packet {
    Header Form (1) = 1,
    Fixed Bit (1) = 1,
    Long Packet Type (2) = 3,
    Unused (4),
    Version (32),
    Destination Connection ID Length (8),
    Destination Connection ID (0..160),
    Source Connection ID Length (8),
    Source Connection ID (0..160),
    Retry Token (..),
    Retry Integrity Tag (128),
}
*/
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class RetryPacket extends LongHeaderPacket {

	public static boolean is(byte type) {
		return (type & 0xf0) == 0xf0;
	}

	byte[] token;
	byte[] tag;

	public RetryPacket(ByteBuffer buffer) {
		super(buffer);
		long length = VariableLengthInteger.parse(buffer).getValue();
		this.token = SimpleBytes.parse(buffer, length).getBytes();
		this.tag = SimpleBytes.parse(buffer, 16).getBytes();
	}

	public RetryPacket(byte type, int version, ConnectionIdPair connIdPair, byte[] token, byte[] tag) {
		super(type, version, connIdPair);
		this.token = token;
		this.tag = tag;
	}
}
