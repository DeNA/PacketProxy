package packetproxy.quic.value.transportparameter.number;

import java.nio.ByteBuffer;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.value.VariableLengthInteger;
import packetproxy.quic.value.transportparameter.TransportParameter;

/* https://datatracker.ietf.org/doc/html/draft-ietf-quic-ack-frequency */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class ExpMinAckDelayParameter extends TransportParameter {

	public static final long ID = 0xff03de1aL;
	long value;

	public ExpMinAckDelayParameter(ByteBuffer buffer) {
		super(buffer);
		value = VariableLengthInteger.parse(super.parameterValue).getValue();
	}

	public ExpMinAckDelayParameter(long value) {
		super(ID, VariableLengthInteger.of(value).getBytes().length, VariableLengthInteger.of(value).getBytes());
		this.value = value;
	}

}
