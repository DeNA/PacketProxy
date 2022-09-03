package packetproxy.quic.value.transportparameter.number;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.value.VariableLengthInteger;
import packetproxy.quic.value.transportparameter.TransportParameter;

import java.nio.ByteBuffer;

/* https://datatracker.ietf.org/doc/html/draft-iyengar-quic-delayed-ack-02#section-3 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class OldMinAckDelayParameter extends TransportParameter {
    static public final long ID = 0xff02de1aL;

    long value;

    public OldMinAckDelayParameter(ByteBuffer buffer) {
        super(buffer);
        value = VariableLengthInteger.parse(super.parameterValue).getValue();
    }

    public OldMinAckDelayParameter(long value) {
        super(ID, VariableLengthInteger.of(value).getBytes().length, VariableLengthInteger.of(value).getBytes());
        this.value = value;
    }

}
