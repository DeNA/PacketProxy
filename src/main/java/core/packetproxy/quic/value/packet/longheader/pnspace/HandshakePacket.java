package packetproxy.quic.value.packet.longheader.pnspace;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.value.key.Key;
import packetproxy.quic.value.packet.longheader.LongHeaderPnSpacePacket;
import packetproxy.quic.value.ConnectionIdPair;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.utils.Constants;

import java.nio.ByteBuffer;

/* Ref: RFC 9000
Handshake Packet {
  Header Form (1) = 1,
  Fixed Bit (1) = 1,
  Long Packet Type (2) = 2,
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
public class HandshakePacket extends LongHeaderPnSpacePacket {

    static public final byte TYPE = (byte)0xe0;

    static public boolean is(byte type) {
        return (type & (byte)0xf0) == TYPE;
    }

    static public HandshakePacket of(int version, ConnectionIdPair connIdPair, PacketNumber packetNumber, byte[] payload) {
        return new HandshakePacket(TYPE, version, connIdPair, packetNumber, payload);
    }

    public HandshakePacket(byte type, int version, ConnectionIdPair connIdPair, PacketNumber packetNumber, byte[] payload) {
        super(type, version, connIdPair, packetNumber, payload);
    }

    public HandshakePacket(ByteBuffer buffer, Key key, PacketNumber largestAckedPn) throws Exception {
        super(buffer, key, largestAckedPn);
    }

    @Override
    public Constants.PnSpaceType getPnSpaceType() {
        return Constants.PnSpaceType.PnSpaceHandshake;
    }
}
