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

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32;

// ref: https://docs.aws.amazon.com/lexv2/latest/dg/event-stream-encoding.html

public record AmazonLexV2(Message[] messages) {
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		AmazonLexV2 that = (AmazonLexV2) obj;
		return Arrays.equals(messages, that.messages);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(messages);
	}

	public record Message(MessageHeader[] headers, byte[] payload) {
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null || getClass() != obj.getClass())
				return false;
			Message message = (Message) obj;
			return Arrays.equals(headers, message.headers) && Arrays.equals(payload, message.payload);
		}

		@Override
		public int hashCode() {
			return Objects.hash(Arrays.hashCode(headers), Arrays.hashCode(payload));
		}
	}

	public record MessageHeader(String headerName, byte headerValueType, String valueString) {
	}

	public static AmazonLexV2 fromBytes(byte[] body) throws Exception {
		if (body == null || body.length == 0) {
			throw new IllegalArgumentException("Body cannot be null or empty");
		}
		var messages = new java.util.ArrayList<Message>();
		int pos = 0;
		while (pos < body.length) {
			int totalByteLength = ((body[pos++] & 0xFF) << 24) | ((body[pos++] & 0xFF) << 16)
					| ((body[pos++] & 0xFF) << 8) | (body[pos++] & 0xFF);

			int headersByteLength = ((body[pos++] & 0xFF) << 24) | ((body[pos++] & 0xFF) << 16)
					| ((body[pos++] & 0xFF) << 8) | (body[pos++] & 0xFF);

			int preludeCRC = ((body[pos++] & 0xFF) << 24) | ((body[pos++] & 0xFF) << 16) | ((body[pos++] & 0xFF) << 8)
					| (body[pos++] & 0xFF);

			java.util.List<MessageHeader> headers = new java.util.ArrayList<>();

			int headerAbsPos = pos + headersByteLength;
			while (pos < headerAbsPos) {
				byte headerNameByteLength = body[pos++];
				String headerName = new String(body, pos, headerNameByteLength);
				pos += headerNameByteLength;

				byte headerValueType = body[pos++];
				short valueStringByteLength = (short) (((body[pos++] & 0xFF) << 8) | (body[pos++] & 0xFF));
				String valueString = new String(body, pos, valueStringByteLength);
				pos += valueStringByteLength;

				headers.add(new MessageHeader(headerName, headerValueType, valueString));
			}

			byte[] payload = new byte[totalByteLength - headersByteLength - 16];
			System.arraycopy(body, pos, payload, 0, payload.length);
			pos += payload.length;

			int messageCRC = ((body[pos++] & 0xFF) << 24) | ((body[pos++] & 0xFF) << 16) | ((body[pos++] & 0xFF) << 8)
					| (body[pos++] & 0xFF);
			if (pos > body.length) {
				throw new Exception("Invalid message length: " + totalByteLength + ", pos: " + pos + ", body length: "
						+ body.length);
			}
			messages.add(new Message(headers.toArray(new MessageHeader[0]), payload));
		}
		return new AmazonLexV2(messages.toArray(new Message[0]));
	}
	public static byte[] toBytes(AmazonLexV2 lex) throws Exception {
		if (lex == null || lex.messages == null || lex.messages.length == 0) {
			throw new IllegalArgumentException("Lex messages cannot be null or empty");
		}
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		for (Message message : lex.messages) {
			int totalByteLength = 16 + message.payload.length;
			int headersByteLength = 0;
			for (MessageHeader header : message.headers) {
				int headerByteLength = 1 + header.headerName.length() + 1 + 2 + header.valueString.length();
				headersByteLength += headerByteLength;
				totalByteLength += headerByteLength;
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write((totalByteLength >> 24) & 0xFF);
			baos.write((totalByteLength >> 16) & 0xFF);
			baos.write((totalByteLength >> 8) & 0xFF);
			baos.write(totalByteLength & 0xFF);

			baos.write((headersByteLength >> 24) & 0xFF);
			baos.write((headersByteLength >> 16) & 0xFF);
			baos.write((headersByteLength >> 8) & 0xFF);
			baos.write(headersByteLength & 0xFF);

			CRC32 crc32 = new CRC32();
			byte[] preludeBytes = baos.toByteArray();
			crc32.update(preludeBytes);
			int preludeCRC = (int) crc32.getValue();
			baos.write((preludeCRC >> 24) & 0xFF);
			baos.write((preludeCRC >> 16) & 0xFF);
			baos.write((preludeCRC >> 8) & 0xFF);
			baos.write(preludeCRC & 0xFF);

			for (MessageHeader header : message.headers) {
				baos.write(header.headerName.length());
				baos.write(header.headerName.getBytes());
				baos.write(header.headerValueType);
				byte[] valueBytes = header.valueString.getBytes("UTF-8");
				baos.write((valueBytes.length >> 8) & 0xFF);
				baos.write(valueBytes.length & 0xFF);
				baos.write(valueBytes);
			}

			baos.write(message.payload);

			CRC32 messageCRC32 = new CRC32();
			byte[] messageBytes = baos.toByteArray();
			messageCRC32.update(messageBytes);
			int messageCRC = (int) messageCRC32.getValue();
			baos.write((messageCRC >> 24) & 0xFF);
			baos.write((messageCRC >> 16) & 0xFF);
			baos.write((messageCRC >> 8) & 0xFF);
			baos.write(messageCRC & 0xFF);

			outputStream.write(baos.toByteArray());
		}
		return outputStream.toByteArray();
	}

}
