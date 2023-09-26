/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Protobuf3Test {
	@Test
	public void testVarint150() throws Exception {
		String data = "089601";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decode(bytes);
		byte[] bytes2 = Protobuf3.encode(json);
		assertArrayEquals(bytes, bytes2);
	}

	@Test
	public void testString() throws Exception {
		String data = "120774657374696e67";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decode(bytes);
		byte[] bytes2 = Protobuf3.encode(json);
		assertArrayEquals(bytes, bytes2);
	}

	@Test
	public void testLong() throws Exception {
		String data = "090102030405060708";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decode(bytes);
		byte[] bytes2 = Protobuf3.encode(json);
		assertArrayEquals(bytes, bytes2);
	}

	@Test
	public void testVarintMinus() throws Exception {
		String data = "08feffffffffffffffff01107b18ffffffffffffffffff01207b";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decode(bytes);
		byte[] bytes2 = Protobuf3.encode(json);
		assertArrayEquals(bytes, bytes2);
	}

	@Test
	public void testComplexUnordered() throws Exception {
		String data = "15c3f548400a410a09e3828fe3819fe3819710d20922105a643bdf4f8df33f2db29defa7c609402a1208011207303830303030301a050dbab126442a0b0801120730383030303030";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decode(bytes);
		byte[] bytes2 = Protobuf3.encode(json);
		assertArrayEquals(bytes, bytes2);
	}

	@Test
	public void testComplex() throws Exception {
		String data = "0a410a09e3828fe3819fe3819710d20922105a643bdf4f8df33f2db29defa7c609402a1208011207303830303030301a050dbab126442a0b080112073038303030303015c3f54840";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decode(bytes);
		byte[] bytes2 = Protobuf3.encode(json);
		assertArrayEquals(bytes, bytes2);
	}

	@Test
	public void testManyField() throws Exception {
		String data = "08011002180320042805600c380740084809500a580b3006";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decode(bytes);
		byte[] bytes2 = Protobuf3.encode(json);
		assertArrayEquals(bytes, bytes2);
	}

	@Test
	public void testEncodeDecodeBytes() throws Exception {
		String data = "0102030405060708090a0b0c0d0e0f101112";
		byte[] bytes = new Binary(new Binary.HexString(data)).toByteArray();
		String json = Protobuf3.decodeBytes(bytes);
		byte[] bytes2 = Protobuf3.encodeBytes(json);
		assertArrayEquals(bytes, bytes2);
	}
}
