package packetproxy.quic.value.transportparameter.number;

import java.nio.ByteBuffer;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.value.VariableLengthInteger;
import packetproxy.quic.value.transportparameter.TransportParameter;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class MaxIdleTimeoutParameter extends TransportParameter {

	public static final long ID = 0x1;
	long value;

	public MaxIdleTimeoutParameter(ByteBuffer buffer) {
		super(buffer);
		value = VariableLengthInteger.parse(super.parameterValue).getValue();
	}

	public MaxIdleTimeoutParameter(long value) {
		super(ID, VariableLengthInteger.of(value).getBytes().length, VariableLengthInteger.of(value).getBytes());
		this.value = value;
	}

}
