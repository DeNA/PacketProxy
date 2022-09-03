package packetproxy.quic.service.transportparameter;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.luminis.tls.extension.Extension;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.transportparameter.*;
import packetproxy.quic.value.transportparameter.bool.DisableActiveMigrationParameter;
import packetproxy.quic.value.transportparameter.bool.ExpGreaseQuicBitParameter;
import packetproxy.quic.value.transportparameter.bytearray.InitSrcConnIdParameter;
import packetproxy.quic.value.transportparameter.bytearray.OrigDestConnIdParameter;
import packetproxy.quic.value.transportparameter.bytearray.RetrySrcConnIdParameter;
import packetproxy.quic.value.transportparameter.bytearray.StatelessResetTokenParameter;
import packetproxy.quic.value.transportparameter.complex.PreferredAddressParameter;
import packetproxy.quic.value.transportparameter.number.*;

import java.nio.ByteBuffer;

@Getter
@Setter
@ToString
public class TransportParameters extends Extension {
    private final Constants.Role role;
    private long initMaxData = 0;
    private long initMaxStreamDataBidiLocal = 0;
    private long initMaxStreamDataBidiRemote = 0;
    private long initMaxStreamBidi = 0;
    private long initMaxStreamDataUni = 0;
    private long initMaxStreamUni = 0;
    private byte[] initSrcConnId = new byte[0];
    private long ackDelayExponent = 3;
    private long activeConnIdLimit = 2;
    private boolean disableActiveMigration = false;
    private long maxAckDelay = 25;
    private long oldMinAckDelay = 0;
    private long expMinAckDelay = 0;
    private long maxIdleTimeout = 0;
    private long maxUdpPayloadSize = 65527;
    private byte[] origDestConnId = new byte[0];
    private byte[] preferredAddress = new byte[0];
    private byte[] retrySrcConnId = new byte[0];
    private byte[] statelessResetToken = new byte[0];
    private long oldTimestamp = 0;
    private boolean expGreaseQuicBit = false;

    public TransportParameters(Constants.Role role) {
        this.role = role;
    }

    public TransportParameters(Constants.Role role, byte[] bytes) throws Exception {
        this(role, ByteBuffer.wrap(bytes));
    }

    public TransportParameters(Constants.Role role, ByteBuffer buffer) throws Exception {
        this.role = role;
        short type = buffer.getShort();
        if (type != 0x39) {
            throw new Exception(String.format("[Error] not TransportParameterExtension (type: %04x)", type));
        }

        short length = buffer.getShort();
        if (length == 0) {
            return;
        }

        int endPosition = buffer.position() + length;
        while (buffer.position() < endPosition) {
            TransportParameter param = TransportParameterParser.parse(buffer);
            setLocalVariableFromTransportParameter(param);
        }
    }

    private void setLocalVariableFromTransportParameter(TransportParameter param) {
        if (param instanceof InitMaxStreamDataBidiLocalParameter) {
            this.initMaxStreamDataBidiLocal = ((InitMaxStreamDataBidiLocalParameter) param).getValue();

        } else if (param instanceof InitMaxStreamDataBidiRemoteParameter) {
            this.initMaxStreamDataBidiRemote = ((InitMaxStreamDataBidiRemoteParameter) param).getValue();

        } else if (param instanceof InitMaxStreamDataUniParameter) {
            this.initMaxStreamDataUni = ((InitMaxStreamDataUniParameter) param).getValue();

        } else if (param instanceof InitMaxStreamBidiParameter) {
            this.initMaxStreamBidi = ((InitMaxStreamBidiParameter) param).getValue();

        } else if (param instanceof InitMaxStreamUniParameter) {
            this.initMaxStreamUni = ((InitMaxStreamUniParameter) param).getValue();

        } else if (param instanceof InitMaxDataParameter) {
            this.initMaxData = ((InitMaxDataParameter) param).getValue();

        } else if (param instanceof InitSrcConnIdParameter) {
            this.initSrcConnId = ((InitSrcConnIdParameter) param).getValue();

        } else if (param instanceof AckDelayExponentParameter) {
            this.ackDelayExponent = ((AckDelayExponentParameter) param).getValue();

        } else if (param instanceof ActiveConnIdLimitParameter) {
            this.activeConnIdLimit = ((ActiveConnIdLimitParameter) param).getValue();

        } else if (param instanceof DisableActiveMigrationParameter) {
            this.disableActiveMigration = true;

        } else if (param instanceof MaxAckDelayParameter) {
            this.maxAckDelay = ((MaxAckDelayParameter) param).getValue();

        } else if (param instanceof MaxIdleTimeoutParameter) {
            this.maxIdleTimeout = ((MaxIdleTimeoutParameter) param).getValue();

        } else if (param instanceof MaxUdpPayloadSizeParameter) {
            this.maxUdpPayloadSize = ((MaxUdpPayloadSizeParameter) param).getValue();

        } else if (param instanceof OrigDestConnIdParameter) {
            this.origDestConnId = ((OrigDestConnIdParameter) param).getValue();

        } else if (param instanceof PreferredAddressParameter) {
            this.preferredAddress = ((PreferredAddressParameter) param).getValue();
            
        } else if (param instanceof RetrySrcConnIdParameter) {
            this.retrySrcConnId = ((RetrySrcConnIdParameter) param).getValue();

        } else if (param instanceof StatelessResetTokenParameter) {
            this.statelessResetToken = ((StatelessResetTokenParameter) param).getValue();

        } else if (param instanceof OldMinAckDelayParameter) {
            this.oldMinAckDelay = ((OldMinAckDelayParameter) param).getValue();

        } else if (param instanceof ExpMinAckDelayParameter) {
            this.expMinAckDelay = ((ExpMinAckDelayParameter) param).getValue();

        } else if (param instanceof OldTimestampParameter) {
            this.oldTimestamp = ((OldTimestampParameter) param).getValue();

        } else if (param instanceof ExpGreaseQuicBitParameter) {
            this.expGreaseQuicBit = true;

        } else if (param instanceof UnknownParameter) {
            System.err.println(String.format("[Error] Unknown Transport Parameter: %s", param));
        }
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer paramsBuffer = ByteBuffer.allocate(1500);
        paramsBuffer.put(new MaxUdpPayloadSizeParameter(this.maxUdpPayloadSize).getBytes());
        paramsBuffer.put(new InitSrcConnIdParameter(this.initSrcConnId).getBytes());
        paramsBuffer.put(new InitMaxDataParameter(this.initMaxData).getBytes());
        paramsBuffer.put(new InitMaxStreamUniParameter(this.initMaxStreamUni).getBytes());
        paramsBuffer.put(new InitMaxStreamBidiParameter(this.initMaxStreamBidi).getBytes());
        paramsBuffer.put(new InitMaxStreamDataBidiLocalParameter(this.initMaxStreamDataBidiLocal).getBytes());
        paramsBuffer.put(new InitMaxStreamDataBidiRemoteParameter(this.initMaxStreamDataBidiRemote).getBytes());
        paramsBuffer.put(new InitMaxStreamDataUniParameter(this.initMaxStreamDataUni).getBytes());
        //paramsBuffer.put(new OldMinAckDelayParameter(this.oldMinAckDelay).getBytes());
        paramsBuffer.put(new AckDelayExponentParameter(this.ackDelayExponent).getBytes());
        paramsBuffer.put(new MaxIdleTimeoutParameter(this.maxIdleTimeout).getBytes());
        if (this.role == Constants.Role.SERVER) {
            paramsBuffer.put(new OrigDestConnIdParameter(this.origDestConnId).getBytes());
            paramsBuffer.put(new ActiveConnIdLimitParameter(this.activeConnIdLimit).getBytes());
            if (this.disableActiveMigration) {
                paramsBuffer.put(new DisableActiveMigrationParameter().getBytes());
            }
        }
        paramsBuffer.flip();

        ByteBuffer buffer = ByteBuffer.allocate(1500);
        buffer.putShort((short)0x39);
        buffer.putShort((short)paramsBuffer.remaining());
        buffer.put(SimpleBytes.parse(paramsBuffer, paramsBuffer.remaining()).getBytes());
        buffer.flip();
        return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
    }
}
