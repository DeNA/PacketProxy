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

package packetproxy.quic.service.packet;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import packetproxy.quic.service.frame.FramesBuilder;
import packetproxy.quic.utils.*;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.packet.QuicPacket;
import packetproxy.quic.value.packet.longheader.pnspace.HandshakePacket;
import packetproxy.quic.value.packet.longheader.pnspace.InitialPacket;
import packetproxy.quic.value.packet.shortheader.ShortHeaderPacket;

@Getter
@NoArgsConstructor(access = AccessLevel.NONE)
public class QuicPacketBuilder {

	public static QuicPacketBuilder getBuilder() {
		return new QuicPacketBuilder();
	}

	Constants.PnSpaceType pnSpaceType;
	byte[] token = new byte[0]; /* for Initial Packet */
	byte[] payload = new byte[0];
	FramesBuilder framesBuilder;
	PacketNumber packetNumber;
	ConnectionIdPair connIdPair;

	public QuicPacketBuilder setPnSpaceType(Constants.PnSpaceType pnSpaceType) {
		this.pnSpaceType = pnSpaceType;
		return this;
	}

	public QuicPacketBuilder setToken(byte[] token) {
		this.token = token;
		return this;
	}

	public QuicPacketBuilder setFramesBuilder(FramesBuilder builder) {
		this.framesBuilder = builder;
		return this;
	}

	public QuicPacketBuilder setConnectionIdPair(ConnectionIdPair connIdPair) {
		this.connIdPair = connIdPair;
		return this;
	}

	public QuicPacketBuilder setPacketNumber(PacketNumber packetNumber) {
		this.packetNumber = packetNumber;
		return this;
	}

	public QuicPacket build() throws Exception {
		this.payload = this.framesBuilder.getBytes();
		if (this.pnSpaceType == Constants.PnSpaceType.PnSpaceInitial) {

			return InitialPacket.of(1, this.connIdPair, this.packetNumber, this.payload, this.token);
		}
		if (this.pnSpaceType == Constants.PnSpaceType.PnSpaceHandshake) {

			return HandshakePacket.of(1, this.connIdPair, this.packetNumber, this.payload);
		}
		if (this.pnSpaceType == Constants.PnSpaceType.PnSpaceApplicationData) {

			return ShortHeaderPacket.of(this.connIdPair.getDestConnId(), this.packetNumber, this.payload);
		}
		throw new Exception("error: unknown packet type");
	}
}
