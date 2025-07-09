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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;

@Value
public class PacketNumber {

	public static final PacketNumber Infinite = new PacketNumber();

	public static PacketNumber of(long number) {
		return new PacketNumber(number);
	}

	public static PacketNumber copy(PacketNumber pn) {
		return new PacketNumber(pn.getNumber());
	}

	public static PacketNumber max(PacketNumber a, PacketNumber b) {
		return a.getNumber() > b.getNumber() ? a : b;
	}

	@Getter(AccessLevel.NONE)
	boolean infinite;
	long number;

	private PacketNumber() {
		this.number = -1;
		this.infinite = true;
	}

	private PacketNumber(long number) {
		assert (number >= 0);
		this.number = number;
		this.infinite = false;
	}

	public boolean isInfinite() {
		return infinite;
	}

	public TruncatedPacketNumber getTruncatedPacketNumber(PacketNumber largestAckedPn) {
		if (!this.infinite) {
			return new TruncatedPacketNumber(this, largestAckedPn);
		}
		return null;
	}

	@SneakyThrows
	public PacketNumber plus(long num) {
		return new PacketNumber(this.number + num);
	}

	@SneakyThrows
	public PacketNumber minus(long num) {
		return new PacketNumber(this.number - num);
	}

	public long minus(PacketNumber packetPn) {
		return this.number - packetPn.number;
	}

	public boolean isLargerThan(PacketNumber packetPn) {
		return this.number > packetPn.getNumber();
	}

	public boolean isLargerThanOrEquals(PacketNumber packetPn) {
		return this.number >= packetPn.getNumber();
	}

	public byte[] toBytes() {
		return new byte[]{(byte) ((this.number >> 24) & 0xff), (byte) ((this.number >> 16) & 0xff),
				(byte) ((this.number >> 8) & 0xff), (byte) (this.number & 0xff)};
	}

	public String toString() {
		return "PacketNumber(" + (this.infinite ? "INF" : this.number) + ")";
	}

}
