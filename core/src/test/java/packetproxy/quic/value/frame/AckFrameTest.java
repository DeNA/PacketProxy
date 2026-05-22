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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AckFrameTest {

	@Test
	public void シンプルなbyteArrayをparseできること() throws Exception {
		byte[] test = Hex.decodeHex("0200030000".toCharArray());
		AckFrame ackFrame = AckFrame.parse(test);
		assertEquals(0, ackFrame.getLargestAcknowledged());
		assertEquals(3, ackFrame.getAckDelay());
		assertEquals(0, ackFrame.getAckRangeCount());
		assertEquals(0, ackFrame.getFirstAckRange());
		Assertions.assertEquals(0, ackFrame.getAckRanges().size());
	}

	@Test
	public void ackRangeがあるbyteArrayをparseできること() throws Exception {
		byte[] test = Hex.decodeHex("020a0001000105".toCharArray());
		AckFrame ackFrame = AckFrame.parse(test);
		assertEquals(10, ackFrame.getLargestAcknowledged());
		assertEquals(0, ackFrame.getFirstAckRange());
		assertEquals(1, ackFrame.getAckRangeCount());
		Assertions.assertEquals(1, ackFrame.getAckRanges().get(0).getGap());
		Assertions.assertEquals(5, ackFrame.getAckRanges().get(0).getAckRangeLength());
	}

	@Test
	public void parseしてgetBytesすると元に戻ること() throws Exception {
		byte[] test = Hex.decodeHex("0200030000".toCharArray());
		AckFrame ackFrame = AckFrame.parse(test);
		byte[] test2 = ackFrame.getBytes();
		assertArrayEquals(test, test2);
	}

	@Test
	public void rangeありのbyteをparseしてgetBytesすると元に戻ること() throws Exception {
		byte[] test = Hex.decodeHex("020a0001000105".toCharArray());
		AckFrame ackFrame = AckFrame.parse(test);
		byte[] test2 = ackFrame.getBytes();
		assertArrayEquals(test, test2);
	}

	@Test
	public void ackRangeがあるbyteArray2つが等しくなること() throws Exception {
		byte[] test1 = Hex.decodeHex("020a0001000105".toCharArray());
		byte[] test2 = Hex.decodeHex("020a0001000105".toCharArray());
		AckFrame ackFrame1 = AckFrame.parse(test1);
		AckFrame ackFrame2 = AckFrame.parse(test2);
		assertThat(ackFrame1).isEqualTo(ackFrame2);
	}
}
