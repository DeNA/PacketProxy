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

import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceInitial;

import java.nio.ByteBuffer;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.utils.Constants.PnSpaceType;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.TruncatedPacketNumber;
import packetproxy.quic.value.VariableLengthInteger;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.value.key.Key;
import packetproxy.quic.value.packet.PnSpacePacket;

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
@EqualsAndHashCode(callSuper = true)
@NonFinal
@Value
public class LongHeaderPnSpacePacket extends LongHeaderPacket implements PnSpacePacket {

	protected PacketNumber packetNumber;
	protected byte[] payload;

	protected LongHeaderPnSpacePacket(byte type, int version, ConnectionIdPair connIdPair, PacketNumber packetNumber,
			byte[] payload) {
		super(type, version, connIdPair);
		this.packetNumber = packetNumber;
		this.payload = payload;
	}

	protected LongHeaderPnSpacePacket(ByteBuffer buffer, Key key, PacketNumber largestAckedPn) throws Exception {
		super(buffer);
		int startPosition = buffer.position() - super.size();
		this.parseExtra(buffer);

		long length = VariableLengthInteger.parse(buffer).getValue();

		// get the sampling data
		int packetNumberPosition = buffer.position();
		buffer.position(buffer.position() + 4);
		byte[] sample = SimpleBytes.parse(buffer, 16).getBytes();

		// get the maskKey from the sampling data
		byte[] maskKey = key.getMaskForHeaderProtection(sample);

		super.unmaskType(PacketHeaderType.LongHeaderType, maskKey);
		int packetNumberLength = super.getOrigPnLength();

		// decode header protection of truncatedPacketNumber
		buffer.position(packetNumberPosition);
		byte[] maskedTruncatedPn = SimpleBytes.parse(buffer, packetNumberLength).getBytes();
		byte[] truncatedPn = TruncatedPacketNumber.unmaskTruncatedPacketNumber(maskedTruncatedPn, maskKey);

		int payloadPosition = buffer.position();
		int payloadLength = (int) length - packetNumberLength;
		byte[] encodedPayload = SimpleBytes.parse(buffer, payloadLength).getBytes();
		int positionPacketEnd = buffer.position();

		buffer.position(startPosition);
		byte[] header = SimpleBytes.parse(buffer, payloadPosition - startPosition).getBytes();
		header[0] = super.getType();
		for (int i = 0; i < truncatedPn.length; i++) {
			header[packetNumberPosition - startPosition + i] = truncatedPn[i];
		}

		this.packetNumber = new TruncatedPacketNumber(truncatedPn).getPacketNumber(largestAckedPn);
		this.payload = key.decryptPayload(this.packetNumber.toBytes(), encodedPayload, header);

		buffer.position(positionPacketEnd);
	}

	@SneakyThrows
	public byte[] getBytes(Key key, PacketNumber largestAckedPn) {
		ByteBuffer headerBuffer = ByteBuffer.allocate(1500);

		byte[] truncatedPn = this.packetNumber.getTruncatedPacketNumber(largestAckedPn).getBytes();
		if (truncatedPn.length + this.payload.length + 16 /* AES auth hash */ < 20) {
			int dummyBytesLength = 20 - truncatedPn.length - this.payload.length - 16;
			truncatedPn = ArrayUtils.addAll(new byte[dummyBytesLength], truncatedPn);
		}

		byte[] payloadLength = VariableLengthInteger.of(truncatedPn.length + payload.length + 16 /* GCM auth hash */)
				.getBytes();

		/* create original header for associated data of AES encryption */
		headerBuffer.put(super.getBytes(truncatedPn.length));
		this.getBytesExtra(headerBuffer);
		headerBuffer.put(payloadLength);
		headerBuffer.put(truncatedPn);
		headerBuffer.flip();
		byte[] header = SimpleBytes.parse(headerBuffer, headerBuffer.remaining()).getBytes();

		byte[] encryptedPayload = key.encryptPayload(truncatedPn, payload, header);
		byte[] sample = ArrayUtils.subarray(ArrayUtils.addAll(truncatedPn, encryptedPayload), 4, 4 + 16);
		byte[] maskKey = key.getMaskForHeaderProtection(sample);
		byte[] maskedTruncatedPn = TruncatedPacketNumber.maskTruncatedPacketNumber(truncatedPn, maskKey);

		ByteBuffer buffer = ByteBuffer.allocate(1500);
		buffer.put(super.getMaskedBytes(truncatedPn.length, maskKey));
		this.getBytesExtra(buffer);
		buffer.put(payloadLength);
		buffer.put(maskedTruncatedPn);
		buffer.put(encryptedPayload);
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	@Override
	@SneakyThrows
	public Frames getFrames() {
		return Frames.parse(this.payload);
	}

	@Override
	@SneakyThrows
	public boolean isAckEliciting() {
		return Frames.parse(this.payload).isAckEliciting();
	}

	@Override
	@SneakyThrows
	public boolean hasAckFrame() {
		return Frames.parse(this.payload).hasAckFrame();
	}

	@Override
	@SneakyThrows
	public Optional<AckFrame> getAckFrame() {
		return Frames.parse(this.payload).getAckFrame();
	}

	@Override
	public PnSpaceType getPnSpaceType() {
		return PnSpaceInitial;
	}

	@Override
	public String toString() {
		return String.format("LongHeaderPacket(version=%d, connIdPair=%s, packetNumber=%s, payload=%s", this.version,
				this.connectionIdPair, this.packetNumber, Frames.parse(this.payload));
	}

	protected void parseExtra(ByteBuffer buffer) {
	};
	protected void getBytesExtra(ByteBuffer buffer) throws Exception {
	};

}
