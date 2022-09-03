package packetproxy.quic.value.packet.shortheader;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.value.key.Key;
import packetproxy.quic.value.packet.PnSpacePacket;
import packetproxy.quic.value.packet.QuicPacket;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.TruncatedPacketNumber;

import java.nio.ByteBuffer;
import java.util.Optional;

/*
https://datatracker.ietf.org/doc/html/rfc9000#section-17.3
1-RTT Packet {
   Header Form (1) = 0,
   Fixed Bit (1) = 1,
   Spin Bit (1),
   Reserved Bits (2),
   Key Phase (1),
   Packet Number Length (2),
   Destination Connection ID (0..160),
   Packet Number (8..32),
   Packet Payload (8..),
}
*/
@EqualsAndHashCode(callSuper = true)
@Value
public class ShortHeaderPacket extends QuicPacket implements PnSpacePacket {

    static public final byte TYPE = (byte)0x40;

    static public boolean is(byte type) {
        return (type & (byte)0xc0) == TYPE;
    }

    static public ConnectionId getDestConnId(ByteBuffer buffer) {
        int savedPosition = buffer.position();
        buffer.get();
        byte[] destConnId = SimpleBytes.parse(buffer, Constants.CONNECTION_ID_SIZE).getBytes();
        buffer.position(savedPosition);
        return ConnectionId.of(destConnId);
    }

    static public ShortHeaderPacket of(ConnectionId destConnId, PacketNumber packetNumber, byte[] payload) {
        return new ShortHeaderPacket(TYPE, destConnId, packetNumber, payload);
    }

    ConnectionId destConnId;
    PacketNumber packetNumber;
    byte[] payload;

    public ShortHeaderPacket(byte type, ConnectionId destConnId, PacketNumber packetNumber, byte[] payload) {
        super(type);
        this.destConnId = destConnId;
        this.packetNumber = packetNumber;
        this.payload = payload;
    }

    public ShortHeaderPacket(ByteBuffer buffer, Key key, PacketNumber largestAckedPn) throws Exception {
        super(buffer);
        int startPosition = buffer.position() - super.size();

        this.destConnId = ConnectionId.parse(buffer, Constants.CONNECTION_ID_SIZE);

        // get the sampling data
        int packetNumberPosition = buffer.position();
        buffer.position(buffer.position() + 4);
        byte[] sample = SimpleBytes.parse(buffer, 16).getBytes();

        // get the maskKey from the sampling data
        byte[] maskKey = key.getMaskForHeaderProtection(sample);

        super.unmaskType(PacketHeaderType.ShortHeaderType, maskKey);
        int packetNumberLength = super.getOrigPnLength();

        // decode header protection of truncatedPacketNumber
        buffer.position(packetNumberPosition);
        byte[] maskedTruncatedPn = SimpleBytes.parse(buffer, packetNumberLength).getBytes();
        byte[] truncatedPn = TruncatedPacketNumber.unmaskTruncatedPacketNumber(maskedTruncatedPn, maskKey);

        int payloadPosition = buffer.position();
        int payloadLength = buffer.limit() - payloadPosition;
        byte[] encodedPayload = SimpleBytes.parse(buffer, payloadLength).getBytes();
        int positionPacketEnd = buffer.position();

        /* create unmasked header for associated data of AES decryption */
        buffer.position(startPosition);
        byte[] header = SimpleBytes.parse(buffer, payloadPosition - startPosition).getBytes();
        header[0] = super.getType();
        for (int i = 0; i < truncatedPn.length; i++) {
            header[packetNumberPosition - startPosition + i] = truncatedPn[i];
        }

        this.payload = key.decryptPayload(truncatedPn, encodedPayload, header);
        this.packetNumber = new TruncatedPacketNumber(truncatedPn).getPacketNumber(largestAckedPn);

        buffer.position(positionPacketEnd);
    }

    public byte[] getBytes(Key key, PacketNumber largestAckedPn) throws Exception {
        ByteBuffer headerBuffer = ByteBuffer.allocate(1500);

        byte[] truncatedPn = this.packetNumber.getTruncatedPacketNumber(largestAckedPn).getBytes();
        if (truncatedPn.length + this.payload.length + 16 /* AES auth hash */ < 20) {
            int dummyBytesLength = 20 - truncatedPn.length - this.payload.length - 16;
            truncatedPn = ArrayUtils.addAll(new byte[dummyBytesLength], truncatedPn);
        }

        /* create original header for associated data of AES encryption */
        byte type = super.getType(truncatedPn.length);
        headerBuffer.put(type);
        headerBuffer.put(this.destConnId.getBytes());
        headerBuffer.put(truncatedPn);
        headerBuffer.flip();
        byte[] header = SimpleBytes.parse(headerBuffer, headerBuffer.remaining()).getBytes();

        byte[] encryptedPayload = key.encryptPayload(truncatedPn, payload, header);
        byte[] sample = ArrayUtils.subarray(ArrayUtils.addAll(truncatedPn, encryptedPayload), 4, 4+16);
        byte[] maskKey = key.getMaskForHeaderProtection(sample);
        byte maskedType = super.getMaskedType(truncatedPn.length, PacketHeaderType.ShortHeaderType, maskKey);
        byte[] maskedTruncatedPn = TruncatedPacketNumber.maskTruncatedPacketNumber(truncatedPn, maskKey);

        ByteBuffer buffer = ByteBuffer.allocate(1500);
        buffer.put(maskedType);
        buffer.put(this.destConnId.getBytes());
        buffer.put(maskedTruncatedPn);
        buffer.put(encryptedPayload);
        buffer.flip();
        return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
    }

    public int size() {
        return this.getBytes().length;
    }

    @Override
    public String toString() {
        try {
            return String.format("ShortHeaderPacket(connIdPair=%s, packetNumber=%s, payload=%s",
                    this.destConnId,
                    this.packetNumber,
                    Frames.parse(this.payload));
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public PacketNumber getPacketNumber() {
        return this.packetNumber;
    }

    @Override
    public boolean isAckEliciting() {
        return Frames.parse(this.payload).isAckEliciting();
    }

    @Override
    public boolean hasAckFrame() {
        return Frames.parse(this.payload).hasAckFrame();
    }

    @Override
    public Optional<AckFrame> getAckFrame() {
        return Frames.parse(this.payload).getAckFrame();
    }

    @Override
    public Constants.PnSpaceType getPnSpaceType() {
        return Constants.PnSpaceType.PnSpaceApplicationData;
    }

    @Override
    public Frames getFrames() {
        Frames frames =  Frames.parse(this.payload);
        return frames;
    }
}
