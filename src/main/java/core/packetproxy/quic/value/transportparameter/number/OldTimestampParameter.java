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
public class OldTimestampParameter extends TransportParameter {

	public static final long ID = 0x7158;
	long value;

	public OldTimestampParameter(ByteBuffer buffer) {
		super(buffer);
		value = VariableLengthInteger.parse(super.parameterValue).getValue();
	}

	public OldTimestampParameter(long value) {
		super(ID, VariableLengthInteger.of(value).getBytes().length, VariableLengthInteger.of(value).getBytes());
		this.value = value;
	}

}
