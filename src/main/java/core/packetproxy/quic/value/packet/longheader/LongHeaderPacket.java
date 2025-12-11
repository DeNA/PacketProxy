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
import lombok.experimental.NonFinal;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.FixedLengthPrecededBytes;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.packet.QuicPacket;

/*
https://www.rfc-editor.org/rfc/rfc9000.html#name-long-header-packets

Long Header Packet {
  Header Form (1) = 1,
  Fixed Bit (1) = 1,
  Long Packet Type (2),
  Type-Specific Bits (4),
  Version (32),
  Destination Connection ID Length (8),
  Destination Connection ID (0..160),
  Source Connection ID Length (8),
  Source Connection ID (0..160),
  Type-Specific Payload (..),
}
*/
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NonFinal
@Value
public class LongHeaderPacket extends QuicPacket {

	public static boolean is(byte type) {
		return (type & (byte) 0xc0) == (byte) 0xc0;
	}

	public static ConnectionId getDestConnId(ByteBuffer buffer) {
		int savedPosition = buffer.position();
		buffer.get();
		buffer.getInt();
		byte[] destConnId = FixedLengthPrecededBytes.parse(buffer).getBytes();
		buffer.position(savedPosition);
		return ConnectionId.of(destConnId);
	}

	protected int version;
	protected ConnectionIdPair connectionIdPair;

	protected LongHeaderPacket(byte type, int version, ConnectionIdPair connIdPair) {
		super(type);
		this.version = version;
		this.connectionIdPair = connIdPair;
	}

	protected LongHeaderPacket(ByteBuffer buffer) {
		super(buffer);
		this.version = buffer.getInt();
		ConnectionId destConnId = ConnectionId.of(FixedLengthPrecededBytes.parse(buffer).getBytes());
		ConnectionId srcConnId = ConnectionId.of(FixedLengthPrecededBytes.parse(buffer).getBytes());
		this.connectionIdPair = ConnectionIdPair.of(srcConnId, destConnId);
	}

	protected int size() {
		return this.getBytes().length;
	}

	protected byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(super.getType());
		buffer.putInt(this.version);
		buffer.put(FixedLengthPrecededBytes.of(this.connectionIdPair.getDestConnId().getBytes()).serialize());
		buffer.put(FixedLengthPrecededBytes.of(this.connectionIdPair.getSrcConnId().getBytes()).serialize());
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	protected byte[] getBytes(int newlyPnLength) {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(super.getType(newlyPnLength));
		buffer.putInt(this.version);
		buffer.put(FixedLengthPrecededBytes.of(this.connectionIdPair.getDestConnId().getBytes()).serialize());
		buffer.put(FixedLengthPrecededBytes.of(this.connectionIdPair.getSrcConnId().getBytes()).serialize());
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	protected byte[] getMaskedBytes(int newlyPnLength, byte[] maskKey) {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(super.getMaskedBytes(newlyPnLength, PacketHeaderType.LongHeaderType, maskKey));
		buffer.putInt(this.version);
		buffer.put(FixedLengthPrecededBytes.of(this.connectionIdPair.getDestConnId().getBytes()).serialize());
		buffer.put(FixedLengthPrecededBytes.of(this.connectionIdPair.getSrcConnId().getBytes()).serialize());
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	public ConnectionId getSrcConnId() {
		return this.connectionIdPair.getSrcConnId();
	}

	public ConnectionId getDestConnId() {
		return this.connectionIdPair.getDestConnId();
	}
}
