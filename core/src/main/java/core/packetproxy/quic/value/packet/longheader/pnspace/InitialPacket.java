package packetproxy.quic.value.packet.longheader.pnspace;

import java.nio.ByteBuffer;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.VariableLengthPrecededBytes;
import packetproxy.quic.value.key.Key;
import packetproxy.quic.value.packet.longheader.LongHeaderPnSpacePacket;

/* Ref: RFC 9000
Initial Packet {
  Header Form (1) = 1,
  Fixed Bit (1) = 1,
  Long Packet Type (2) = 0,
  Reserved Bits (2),
  Packet Number Length (2),
  Version (32),
  Destination Connection ID Length (8),
  Destination Connection ID (0..160),
  Source Connection ID Length (8),
  Source Connection ID (0..160),
  Token Length (i),
  Token (..),
  Length (i),
  Packet Number (8..32),
  Packet Payload (8..),
}
*/
@EqualsAndHashCode(callSuper = true)
@Value
public class InitialPacket extends LongHeaderPnSpacePacket {

	public static final byte TYPE = (byte) 0xc0;

	public static boolean is(byte type) {
		return (type & (byte) 0xf0) == TYPE;
	}

	public static InitialPacket of(int version, ConnectionIdPair connIdPair, PacketNumber packetNumber, byte[] payload,
			byte[] token) {
		return new InitialPacket(TYPE, version, connIdPair, packetNumber, payload, token);
	}

	@NonFinal
	byte[] token;

	public InitialPacket(byte type, int version, ConnectionIdPair connIdPair, PacketNumber packetNumber, byte[] payload,
			byte[] token) {
		super(type, version, connIdPair, packetNumber, payload);
		this.token = token;
	}

	public InitialPacket(ByteBuffer buffer, Key key, PacketNumber largestAckedPn) throws Exception {
		super(buffer, key, largestAckedPn);
	}

	@Override
	protected void parseExtra(ByteBuffer buffer) {
		this.token = VariableLengthPrecededBytes.parse(buffer).getBytes();
	}

	@Override
	protected void getBytesExtra(ByteBuffer buffer) {
		buffer.put(VariableLengthPrecededBytes.of(token).serialize());
	}

	@Override
	public Constants.PnSpaceType getPnSpaceType() {
		return Constants.PnSpaceType.PnSpaceInitial;
	}

	@Override
	public String toString() {
		return String.format("InitialPacket(token=[%s], super=%s", Hex.encodeHexString(this.token), super.toString());
	}
}
