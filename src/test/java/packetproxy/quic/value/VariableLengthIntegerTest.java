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

package packetproxy.quic.value;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

/* https://tools.ietf.org/html/draft-ietf-quic-transport-19#section-16 */
class VariableLengthIntegerTest {

	@Test
	void smoke() throws Exception {
		VariableLengthInteger t = VariableLengthInteger.parse(Hex.decodeHex("800061a8".toCharArray()));
		assertEquals(25000, t.getValue());
	}

	@Test
	void parseSingleByte() throws Exception {
		// "and the single byte 25 decodes to 37"
		byte[] testBytes = Hex.decodeHex("25".toCharArray());
		VariableLengthInteger var = VariableLengthInteger.parse(ByteBuffer.wrap(testBytes));

		assertEquals(37, var.getValue());
		assertArrayEquals(testBytes, var.getBytes());
	}

	@Test
	void parseTwoBytes() throws Exception {
		// "the two byte sequence 7b bd decodes to 15293; "
		byte[] testBytes = Hex.decodeHex("7bbd".toCharArray());
		VariableLengthInteger var = VariableLengthInteger.parse(ByteBuffer.wrap(testBytes));

		assertEquals(15293, var.getValue());
		assertArrayEquals(testBytes, var.getBytes());
	}

	@Test
	void parseTwoBytes2() throws Exception {
		// "(as does the two byte sequence 40 25)"
		byte[] testBytes = Hex.decodeHex("4025".toCharArray());
		VariableLengthInteger var = VariableLengthInteger.parse(ByteBuffer.wrap(testBytes));

		assertEquals(37, var.getValue());
	}

	@Test
	void parseFourBytes() throws Exception {
		// "the four byte sequence 9d 7f 3e 7d decodes to 494878333;"
		byte[] testBytes = Hex.decodeHex("9d7f3e7d".toCharArray());
		VariableLengthInteger var = VariableLengthInteger.parse(ByteBuffer.wrap(testBytes));

		assertEquals(494878333, var.getValue());
		assertArrayEquals(testBytes, var.getBytes());
	}

	@Test
	void parseEightBytes() throws Exception {
		// "the eight byte sequence c2 19 7c 5e ff 14 e8 8c decodes to
		// 151288809941952652;"
		byte[] testBytes = Hex.decodeHex("c2197c5eff14e88c".toCharArray());
		VariableLengthInteger var = VariableLengthInteger.parse(ByteBuffer.wrap(testBytes));

		assertEquals(151288809941952652L, var.getValue());
		assertArrayEquals(testBytes, var.getBytes());
	}

	@Test
	void parseExampleBytes() throws Exception {
		System.out.println(VariableLengthInteger.parse(Hex.decodeHex("80200000".toCharArray()))); /* 2MB */
		System.out.println(VariableLengthInteger.parse(Hex.decodeHex("80100000".toCharArray()))); /* 1MB */
		System.out.println(VariableLengthInteger.parse(Hex.decodeHex("4201".toCharArray()))); /* 513 */
		System.out.println(VariableLengthInteger.parse(Hex.decodeHex("4077".toCharArray()))); /* 119 */
		System.out.println(VariableLengthInteger.parse(Hex.decodeHex("58cb".toCharArray()))); /* 6347 */
		System.out.printf("%x\n", VariableLengthInteger.parse(Hex.decodeHex("f684f228323451e8".toCharArray()))
				.getValue()); /* 3684f228323451e8 */
		System.out.printf("%x\n", VariableLengthInteger.parse(Hex.decodeHex("00".toCharArray())).getValue()); /* 0 */
	}

}
