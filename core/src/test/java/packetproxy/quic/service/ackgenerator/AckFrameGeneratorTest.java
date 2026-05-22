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

package packetproxy.quic.service.ackgenerator;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.service.framegenerator.AckFrameGenerator;
import packetproxy.quic.value.frame.AckFrame;

class AckFrameGeneratorTest {

	@Test
	public void 全て受信() throws Exception {
		AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();

		ackFrameGenerator.received(0);
		ackFrameGenerator.received(1);
		ackFrameGenerator.received(2);
		ackFrameGenerator.received(3);

		AckFrame ackFrame = ackFrameGenerator.generateAckFrame();
		assertEquals(3, ackFrame.getLargestAcknowledged());
		assertEquals(3, ackFrame.getFirstAckRange());
		assertEquals(0, ackFrame.getAckRangeCount());
	}

	@Test
	public void 全て受信ただし開始番号が途中() throws Exception {
		AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();

		ackFrameGenerator.received(5);
		ackFrameGenerator.received(6);
		ackFrameGenerator.received(7);
		ackFrameGenerator.received(8);

		AckFrame ackFrame = ackFrameGenerator.generateAckFrame();
		assertEquals(8, ackFrame.getLargestAcknowledged());
		assertEquals(3, ackFrame.getFirstAckRange());
		assertEquals(0, ackFrame.getAckRangeCount());
	}

	@Test
	public void 受信できていないpacketが存在() throws Exception {
		AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();

		ackFrameGenerator.received(100);

		AckFrame ackFrame = ackFrameGenerator.generateAckFrame();
		assertEquals(100, ackFrame.getLargestAcknowledged());
		assertEquals(0, ackFrame.getFirstAckRange());
		assertEquals(0, ackFrame.getAckRangeCount());
	}

	@Test
	public void 受信できていないpacketが複数存在() throws Exception {
		AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();

		ackFrameGenerator.received(2);
		ackFrameGenerator.received(3);
		ackFrameGenerator.received(4);
		ackFrameGenerator.received(5);
		ackFrameGenerator.received(6);
		ackFrameGenerator.received(7);
		ackFrameGenerator.received(10);

		AckFrame ackFrame = ackFrameGenerator.generateAckFrame();
		assertEquals(10, ackFrame.getLargestAcknowledged());
		assertEquals(0, ackFrame.getFirstAckRange());
		assertEquals(1, ackFrame.getAckRangeCount());
		assertEquals(1, ackFrame.getAckRanges().get(0).getGap());
		assertEquals(5, ackFrame.getAckRanges().get(0).getAckRangeLength());
	}

	@Test
	public void 最初のpacketを受信した後getBytesできること() throws Exception {
		AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();
		ackFrameGenerator.received(0);
		AckFrame ackFrame = ackFrameGenerator.generateAckFrame();

		assertEquals(0, ackFrame.getLargestAcknowledged());
		assertEquals(0, ackFrame.getFirstAckRange());
		assertEquals(0, ackFrame.getAckRangeCount());
		assertArrayEquals(Hex.decodeHex("0200000000".toCharArray()), ackFrame.getBytes());
	}

	@Test
	public void 相手に受信されるとAckFrameは生成されない() throws Exception {
		AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();
		ackFrameGenerator.received(2);
		ackFrameGenerator.received(4);
		ackFrameGenerator.received(6);
		AckFrame ackFrame = ackFrameGenerator.generateAckFrame();
		ackFrameGenerator.confirmedAckFrame(ackFrame);
		AckFrame ackFrame2 = ackFrameGenerator.generateAckFrame();

		assertNotNull(ackFrame);
		assertNull(ackFrame2);
	}

	@Test
	public void 相手に受信された後さらに受信すると差分のAckFrameが生成される() throws Exception {
		AckFrameGenerator ackFrameGenerator = new AckFrameGenerator();
		ackFrameGenerator.received(2);
		ackFrameGenerator.received(4);
		ackFrameGenerator.received(6);
		AckFrame ackFrame = ackFrameGenerator.generateAckFrame();
		ackFrameGenerator.received(7);
		ackFrameGenerator.received(9);
		ackFrameGenerator.confirmedAckFrame(ackFrame);
		AckFrame ackFrame2 = ackFrameGenerator.generateAckFrame();

		assertNotNull(ackFrame);
		assertNotNull(ackFrame2);
		assertEquals(9, ackFrame2.getLargestAcknowledged());
		assertEquals(0, ackFrame2.getFirstAckRange());
		assertEquals(1, ackFrame2.getAckRangeCount());
		assertEquals(0, ackFrame2.getAckRanges().get(0).getGap());
		assertEquals(0, ackFrame2.getAckRanges().get(0).getAckRangeLength());
	}
}
