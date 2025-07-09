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

package packetproxy.quic.value.frame;

import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;
import lombok.*;
import packetproxy.quic.value.VariableLengthInteger;
import packetproxy.quic.value.frame.helper.AckRanges;

/* RFC9000 19.3
ACK Frame {
  Type (i) = 0x02..0x03,
  Largest Acknowledged (i),
  ACK Delay (i),
  ACK Range Count (i),
  First ACK Range (i),
  ACK Range (..) ...,
  [ECN Counts (..)], // if type is 0x03
}

ACK Range {
  Gap (i),
  ACK Range Length (i),
}

ECN Counts {
  ECT0 Count (i),
  ECT1 Count (i),
  ECN-CE Count (i),
}
*/

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class AckEcnFrame extends AckFrame {

	public static final byte TYPE = 0x03;

	public static List<Byte> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	long etc0Count;
	long etc1Count;
	long ecnCeCount;

	public static AckEcnFrame parse(byte[] bytes) {
		return AckEcnFrame.parse(ByteBuffer.wrap(bytes));
	}

	public static AckEcnFrame parse(ByteBuffer buffer) {
		AckFrame ackFrame = AckFrame.parse(buffer);
		long etc0Count = VariableLengthInteger.parse(buffer).getValue();
		long etc1Count = VariableLengthInteger.parse(buffer).getValue();
		long ecnCeCount = VariableLengthInteger.parse(buffer).getValue();
		return new AckEcnFrame(ackFrame.getLargestAcknowledged(), ackFrame.getAckDelay(), ackFrame.getAckRangeCount(),
				ackFrame.getFirstAckRange(), ackFrame.getAckRanges(), etc0Count, etc1Count, ecnCeCount);
	}

	public AckEcnFrame(long largestAcknowledged, long ackDelay, long ackRangeCount, long firstAckRange,
			AckRanges ackRanges, long etc0Count, long etc1Count, long ecnCeCount) {
		super(largestAcknowledged, ackDelay, ackRangeCount, firstAckRange, ackRanges);
		this.etc0Count = etc0Count;
		this.etc1Count = etc1Count;
		this.ecnCeCount = ecnCeCount;
	}

	@Override
	public boolean isAckEliciting() {
		return false;
	}

}
