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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.service.frame.Frames;

class FramesTest {

	@Test
	public void clientHelloSample() throws Exception {
		byte[] testData = Hex.decodeHex(
				"060040f1010000ed0303ebf8fa56f12939b9584a3896472ec40bb863cfd3e86804fe3a47f06a2b69484c00000413011302010000c000000010000e00000b6578616d706c652e636f6dff01000100000a00080006001d0017001800100007000504616c706e000500050100000000003300260024001d00209370b2c9caa47fbabaf4559fedba753de171fa71f50f1ce15d43e994ec74d748002b0003020304000d0010000e0403050306030203080408050806002d00020101001c00024001003900320408ffffffffffffffff05048000ffff07048000ffff0801100104800075300901100f088394c8f03e51570806048000ffff"
						.toCharArray());
		List<Frame> frames = Frames.parse(testData).getFrames();
		assertEquals(1, frames.size());
		assertTrue(frames.get(0) instanceof CryptoFrame);
	}

	@Test
	public void serverHelloSample() throws Exception {
		byte[] testData = Hex.decodeHex(
				"02000300000600407b020000770303afea1ef3ac12e018d10201ef251dab9ca22fb662faa45b5b151126b9a7550a7400130100004f002b000203040033004500170041047254be2b315b10396eeea8b68d454772bf5817e99cb88f5f4aaec6fd12beca033448965c0fd649cc59e6c90698a4f50d388a5c3722fba5770dfbcdbb5063210d0000000000000000"
						.toCharArray());
		List<Frame> frames = Frames.parse(testData).getFrames();
		assertEquals(3, frames.size());
		assertTrue(frames.get(0) instanceof AckFrame);
		assertTrue(frames.get(1) instanceof CryptoFrame);
		assertTrue(frames.get(2) instanceof PaddingFrame);
		for (Frame frame : frames) {

			System.out.println(frame.toString());
		}
	}
}
