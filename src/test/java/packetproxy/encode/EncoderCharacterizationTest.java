/*
 * Copyright 2025 DeNA Co., Ltd.
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
package packetproxy.encode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for core encoders. These pin the existing
 * encode/decode behaviour so that regressions caused by the CLI refactor are
 * caught immediately.
 */
public class EncoderCharacterizationTest {

	// ─── EncodeSample (identity) ──────────────────────────────────────────────

	@Test
	public void sampleDecodeClientRequestIsIdentity() throws Exception {
		Encoder enc = new EncodeSample(null);
		byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(input, enc.decodeClientRequest(input));
	}

	@Test
	public void sampleEncodeClientRequestIsIdentity() throws Exception {
		Encoder enc = new EncodeSample(null);
		byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(input, enc.encodeClientRequest(input));
	}

	@Test
	public void sampleDecodeServerResponseIsIdentity() throws Exception {
		Encoder enc = new EncodeSample(null);
		byte[] input = "response data".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(input, enc.decodeServerResponse(input));
	}

	@Test
	public void sampleEncodeServerResponseIsIdentity() throws Exception {
		Encoder enc = new EncodeSample(null);
		byte[] input = "response data".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(input, enc.encodeServerResponse(input));
	}

	@Test
	public void sampleHandlesEmptyInput() throws Exception {
		Encoder enc = new EncodeSample(null);
		assertArrayEquals(new byte[0], enc.decodeClientRequest(new byte[0]));
		assertArrayEquals(new byte[0], enc.encodeClientRequest(new byte[0]));
		assertArrayEquals(new byte[0], enc.decodeServerResponse(new byte[0]));
		assertArrayEquals(new byte[0], enc.encodeServerResponse(new byte[0]));
	}

	@Test
	public void sampleHandlesBinaryInput() throws Exception {
		Encoder enc = new EncodeSample(null);
		byte[] input = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
		assertArrayEquals(input, enc.decodeClientRequest(input));
		assertArrayEquals(input, enc.encodeClientRequest(input));
		assertArrayEquals(input, enc.decodeServerResponse(input));
		assertArrayEquals(input, enc.encodeServerResponse(input));
	}

	// ─── EncodeSampleUpperCase ────────────────────────────────────────────────

	@Test
	public void upperCaseDecodeClientRequestUppercases() throws Exception {
		Encoder enc = new EncodeSampleUpperCase(null);
		byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
		byte[] expected = "HELLO".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(expected, enc.decodeClientRequest(input));
	}

	@Test
	public void upperCaseEncodeClientRequestLowercases() throws Exception {
		Encoder enc = new EncodeSampleUpperCase(null);
		byte[] input = "HELLO".getBytes(StandardCharsets.UTF_8);
		byte[] expected = "hello".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(expected, enc.encodeClientRequest(input));
	}

	@Test
	public void upperCaseDecodeServerResponseUppercases() throws Exception {
		Encoder enc = new EncodeSampleUpperCase(null);
		byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
		byte[] expected = "HELLO".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(expected, enc.decodeServerResponse(input));
	}

	@Test
	public void upperCaseEncodeServerResponseLowercases() throws Exception {
		Encoder enc = new EncodeSampleUpperCase(null);
		byte[] input = "HELLO".getBytes(StandardCharsets.UTF_8);
		byte[] expected = "hello".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(expected, enc.encodeServerResponse(input));
	}

	@Test
	public void upperCaseClientRequestRoundTrip() throws Exception {
		Encoder enc = new EncodeSampleUpperCase(null);
		byte[] original = "Hello World".getBytes(StandardCharsets.UTF_8);
		byte[] decoded = enc.decodeClientRequest(original);
		byte[] reEncoded = enc.encodeClientRequest(decoded);
		assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), reEncoded);
	}

	@Test
	public void upperCaseServerResponseRoundTrip() throws Exception {
		Encoder enc = new EncodeSampleUpperCase(null);
		byte[] original = "Hello World".getBytes(StandardCharsets.UTF_8);
		byte[] decoded = enc.decodeServerResponse(original);
		byte[] reEncoded = enc.encodeServerResponse(decoded);
		assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), reEncoded);
	}
}
