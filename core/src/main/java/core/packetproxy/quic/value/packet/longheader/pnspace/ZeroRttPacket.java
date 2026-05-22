package packetproxy.quic.value.packet.longheader.pnspace;

import static packetproxy.quic.utils.Constants.PnSpaceType.PnSpaceApplicationData;

import java.nio.ByteBuffer;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.utils.Constants.PnSpaceType;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.key.Key;
import packetproxy.quic.value.packet.longheader.LongHeaderPnSpacePacket;

/* Ref: RFC 9000 (0RTT Packet)
0-RTT Packet {
  Header Form (1) = 1,
  Fixed Bit (1) = 1,
  Long Packet Type (2) = 1,
  Reserved Bits (2),
  Packet Number Length (2),
  Version (32),
  Destination Connection ID Length (8),
  Destination Connection ID (0..160),
  Source Connection ID Length (8),
  Source Connection ID (0..160),
  Length (i),
  Packet Number (8..32),
  Packet Payload (8..),
}
*/
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class ZeroRttPacket extends LongHeaderPnSpacePacket {

	public static final byte TYPE = (byte) 0xd0;

	public static boolean is(byte type) {
		return (type & (byte) 0xf0) == TYPE;
	}

	public static ZeroRttPacket of(int version, ConnectionIdPair connIdPair, PacketNumber packetNumber,
			byte[] payload) {
		return new ZeroRttPacket(TYPE, version, connIdPair, packetNumber, payload);
	}

	public ZeroRttPacket(byte type, int version, ConnectionIdPair connIdPair, PacketNumber packetNumber,
			byte[] payload) {
		super(type, version, connIdPair, packetNumber, payload);
	}

	public ZeroRttPacket(ByteBuffer buffer, Key key, PacketNumber largestAckedPn) throws Exception {
		super(buffer, key, largestAckedPn);
	}

	@Override
	public PnSpaceType getPnSpaceType() {
		return PnSpaceApplicationData;
	}
}
