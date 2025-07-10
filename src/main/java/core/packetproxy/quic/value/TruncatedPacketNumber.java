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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;

@Value
public class TruncatedPacketNumber {

	@Getter(AccessLevel.NONE)
	byte[] truncatedPacketNumber;

	public TruncatedPacketNumber(byte[] truncatedPacketNumber) {
		this.truncatedPacketNumber = truncatedPacketNumber;
	}

	public TruncatedPacketNumber(PacketNumber packetNumber, PacketNumber largestAckPn) {
		this.truncatedPacketNumber = this.encode(packetNumber, largestAckPn);
	}

	public static byte[] unmaskTruncatedPacketNumber(byte[] truncatedPacketNumber, byte[] maskKey) {
		return maskTruncatedPacketNumber(truncatedPacketNumber, maskKey);
	}

	public static byte[] maskTruncatedPacketNumber(byte[] truncatedPacketNumber, byte[] maskKey) {
		ByteBuffer pn = ByteBuffer.allocate(truncatedPacketNumber.length);
		for (int i = 0; i < truncatedPacketNumber.length; i++) {

			pn.put((byte) (truncatedPacketNumber[i] ^ maskKey[1 + i]));
		}
		pn.flip();
		return pn.array();
	}

	public byte[] getBytes() {
		return this.truncatedPacketNumber;
	}

	public PacketNumber getPacketNumber(PacketNumber largestAckedPn) {
		long packetNumber = decode(this.truncatedPacketNumber, largestAckedPn);
		return PacketNumber.of(packetNumber);
	}

	public String toString() {
		return String.format("TruncatedPacketNumber([%s])", Hex.encodeHexString(this.truncatedPacketNumber));
	}

	/* https://www.rfc-editor.org/rfc/rfc9000.html#section-a.2 */
	private static byte[] encode(PacketNumber packetNumber, PacketNumber largestAckPn) {
		/*
		 * The number of bits must be at least one more
		 * than the base-2 logarithm of the number of contiguous
		 * unacknowledged packet numbers, including the new packet.
		 */
		long numUnAcked = (largestAckPn.isInfinite())
				? packetNumber.getNumber() + 1
				: packetNumber.getNumber() - largestAckPn.getNumber();

		double minBits = Math.log(numUnAcked) / Math.log(2) + 1;
		int numBytes = (int) Math.ceil(minBits / 8.0f);

		/* truncate to the least significant bytes. */
		return truncate(packetNumber.getNumber(), numBytes);
	}

	/* https://www.rfc-editor.org/rfc/rfc9000.html#section-a.1 */
	private static long decode(byte[] truncatedPacketNumberBytes, PacketNumber largestAckPn) {
		long truncatedPn = bytesToLong(truncatedPacketNumberBytes);
		int bits = truncatedPacketNumberBytes.length * 8;
		long expectedPn = largestAckPn.getNumber() + 1;
		long pnWindow = 1L << bits;
		long pnHalfWindow = pnWindow / 2;
		long pnMask = ~(pnWindow - 1);

		long candidatePn = (expectedPn & pnMask) | truncatedPn;
		if (candidatePn <= expectedPn - pnHalfWindow && candidatePn < (1L << 62) - pnWindow) {

			return candidatePn + pnWindow;
		}
		if (candidatePn > expectedPn + pnHalfWindow && candidatePn >= pnWindow) {

			return candidatePn - pnWindow;
		}
		return candidatePn;
	}

	private static long bytesToLong(byte[] bytes) {
		switch (bytes.length) {

			case 1 :
				return bytes[0] & 0xff;
			case 2 :
				return ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
			case 3 :
				return ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
			case 4 :
				return ((long) (bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8)
						| (bytes[3] & 0xff);
			default :
				System.err.println("[Error] can't decode packetNumber from ByteArray to Long");
				return 0;
		}
	}

	private static byte[] truncate(long packetNumber, int byteLength) {
		if (byteLength == 0 || byteLength == 1) {

			return new byte[]{(byte) (packetNumber & 0xff)};
		} else if (byteLength == 2) {

			return new byte[]{(byte) ((packetNumber >> 8) & 0xff), (byte) (packetNumber & 0xff)};
		} else if (byteLength == 3) {

			return new byte[]{(byte) ((packetNumber >> 16) & 0xff), (byte) ((packetNumber >> 8) & 0xff),
					(byte) (packetNumber & 0xff)};
		} else if (byteLength >= 4) {

			return new byte[]{(byte) ((packetNumber >> 24) & 0xff), (byte) ((packetNumber >> 16) & 0xff),
					(byte) ((packetNumber >> 8) & 0xff), (byte) (packetNumber & 0xff)};
		}
		System.err.println(
				String.format("[Error] can't encode packetNumber from Long to ByteArray (byteLength=%d)", byteLength));
		return null;
	}
}
