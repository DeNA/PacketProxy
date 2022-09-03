package packetproxy.quic.value.transportparameter.number;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.value.VariableLengthInteger;
import packetproxy.quic.value.transportparameter.TransportParameter;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class InitMaxStreamUniParameter extends TransportParameter {
    static public final long ID = 0x9;
    long value;

    public InitMaxStreamUniParameter(ByteBuffer buffer) {
        super(buffer);
        this.value = VariableLengthInteger.parse(super.parameterValue).getValue();
    }

    public InitMaxStreamUniParameter(long value) {
        super(ID, VariableLengthInteger.of(value).getBytes().length, VariableLengthInteger.of(value).getBytes());
        this.value = value;
    }

}
