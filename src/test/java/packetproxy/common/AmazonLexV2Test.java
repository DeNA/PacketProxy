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
package packetproxy.common;

import org.junit.jupiter.api.Test;

public class AmazonLexV2Test {

	@Test
	public void testToBytesAndFromBytes() throws Exception {
		AmazonLexV2.MessageHeader[] headers = {
				new AmazonLexV2.MessageHeader(":content-type", (byte) 7, "application/json"),
				new AmazonLexV2.MessageHeader(":event-type", (byte) 7, "text")};

		byte[] payload = "Hello World".getBytes("UTF-8");

		AmazonLexV2.Message message = new AmazonLexV2.Message(headers, payload);
		AmazonLexV2.Message[] messages = {message};

		AmazonLexV2 original = new AmazonLexV2(messages);

		byte[] bytes = AmazonLexV2.toBytes(original);
		AmazonLexV2 decoded = AmazonLexV2.fromBytes(bytes);

		assert (original.equals(decoded));
	}

	@Test
	public void testToBytesAndFromBytesMultipleMessages() throws Exception {
		AmazonLexV2.MessageHeader[] headers1 = {
				new AmazonLexV2.MessageHeader(":content-type", (byte) 7, "application/json")};
		AmazonLexV2.MessageHeader[] headers2 = {new AmazonLexV2.MessageHeader(":event-type", (byte) 7, "audio")};

		byte[] payload1 = "First message".getBytes("UTF-8");
		byte[] payload2 = "Second message".getBytes("UTF-8");

		AmazonLexV2.Message message1 = new AmazonLexV2.Message(headers1, payload1);
		AmazonLexV2.Message message2 = new AmazonLexV2.Message(headers2, payload2);
		AmazonLexV2.Message[] messages = {message1, message2};

		AmazonLexV2 original = new AmazonLexV2(messages);

		byte[] bytes = AmazonLexV2.toBytes(original);
		AmazonLexV2 decoded = AmazonLexV2.fromBytes(bytes);

		assert (original.equals(decoded));
	}

	@Test
	public void testEmptyPayload() throws Exception {
		AmazonLexV2.MessageHeader[] headers = {new AmazonLexV2.MessageHeader(":event-type", (byte) 7, "heartbeat")};

		byte[] payload = new byte[0];

		AmazonLexV2.Message message = new AmazonLexV2.Message(headers, payload);
		AmazonLexV2.Message[] messages = {message};

		AmazonLexV2 original = new AmazonLexV2(messages);

		byte[] bytes = AmazonLexV2.toBytes(original);
		AmazonLexV2 decoded = AmazonLexV2.fromBytes(bytes);

		assert (original.equals(decoded));
	}
}
