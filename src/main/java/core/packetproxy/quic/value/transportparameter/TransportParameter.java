/*
 * Copyright 2022 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package packetproxy.quic.value.transportparameter;

import java.nio.ByteBuffer;
import lombok.*;
import lombok.experimental.NonFinal;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

/*
https://www.rfc-editor.org/rfc/rfc9000.html#transport-parameter-encoding

Transport Parameter {
  Transport Parameter ID (i),
  Transport Parameter Length (i),
  Transport Parameter Value (..),
}

// Old Transport Parameters
ID: Description
======================
0x0020: datagram
0x0040: multi path
0x1057: loss bits
0x173e: discard
0x2ab2: grease quic bit     // https://datatracker.ietf.org/doc/html/draft-ietf-quic-bit-grease
0x7157: timestamp           // https://datatracker.ietf.org/doc/html/draft-huitema-quic-ts-02#section-5
0x7158: timestamp           // https://datatracker.ietf.org/doc/html/draft-huitema-quic-ts-05#section-5
0x73db: version negotiation // https://datatracker.ietf.org/doc/html/draft-ietf-quic-version-negotiation-03#section-12.1
0xff73db: version negotiation // https://datatracker.ietf.org/doc/html/draft-ietf-quic-version-negotiation-08
0xde1a: min ack delay       // https://datatracker.ietf.org/doc/html/draft-iyengar-quic-delayed-ack-01#section-3
0xff02de1a: min ack delay   // https://datatracker.ietf.org/doc/html/draft-iyengar-quic-delayed-ack-02#section-3
0xff03de1a: min ack delay   // https://datatracker.ietf.org/doc/html/draft-ietf-quic-ack-frequency
*/

@NonFinal
@AllArgsConstructor
@Value
public abstract class TransportParameter {
	protected long parameterId;
	protected long parameterLength;
	protected byte[] parameterValue;

	public TransportParameter(ByteBuffer buffer) {
		this.parameterId = VariableLengthInteger.parse(buffer).getValue();
		this.parameterLength = VariableLengthInteger.parse(buffer).getValue();
		this.parameterValue = SimpleBytes.parse(buffer, this.parameterLength).getBytes();
	}

	public byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(4096);
		buffer.put(VariableLengthInteger.of(this.parameterId).getBytes());
		buffer.put(VariableLengthInteger.of(this.parameterLength).getBytes());
		buffer.put(this.parameterValue);
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}
}
