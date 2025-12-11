/*
 * Copyright 2019 shioshiota
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

import static packetproxy.util.Logging.errWithStackTrace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;
import net.arnx.jsonic.JSON;
import org.xbill.DNS.utils.base64;

public class GRPCMessage {

	private static final int GRPC_WEB_FH_DATA = 0b0;
	private static final int GRPC_WEB_FH_TRAILER = 0b10000000;

	public int type;
	public Map<String, Object> message;

	public static List<GRPCMessage> decodeTextMessages(String base64Str) throws Exception {
		return decodeMessages(base64.fromString(base64Str));
	}

	public static List<GRPCMessage> decodeMessages(byte[] bytes) throws Exception {
		ByteArrayInputStream bio = new ByteArrayInputStream(bytes);
		List<GRPCMessage> ret = new ArrayList<>();
		while (bio.available() > 0) {

			ret.add(new GRPCMessage(bio));
		}
		return ret;
	}

	public static String encodeTextMessages(List<Map<String, Object>> messages) throws Exception {
		return new String(encodeMessages(messages, (grpcMessage) -> {
			try {

				return base64.toString(grpcMessage.toBytes()).getBytes();
			} catch (Exception e) {

				errWithStackTrace(e);
				return new byte[0];
			}
		}));
	}

	public static byte[] encodeMessages(List<Map<String, Object>> messages) throws Exception {
		return encodeMessages(messages, (grpcMessage) -> {
			try {

				return grpcMessage.toBytes();
			} catch (Exception e) {

				errWithStackTrace(e);
				return new byte[0];
			}
		});
	}

	private static byte[] encodeMessages(List<Map<String, Object>> messages, Function<GRPCMessage, byte[]> f)
			throws Exception {
		var buf = new ByteArrayOutputStream();
		for (var message : messages) {

			var grpcMessage = new GRPCMessage(message);
			var encodedMessage = f.apply(grpcMessage);
			buf.writeBytes(encodedMessage);
		}
		return buf.toByteArray();
	}

	public GRPCMessage(InputStream bio) throws Exception {
		type = bio.read();
		int length = 0;
		for (int i = 0; i < 4; i++) {

			length <<= 8;
			length += bio.read();
		}
		byte[] raw = new byte[length];
		bio.read(raw);
		if (type == GRPC_WEB_FH_DATA) {

			message = JSON.decode(Protobuf3.decode(raw));
		} else if (type == GRPC_WEB_FH_TRAILER) {

			message = new HashMap<>();
			String str = new String(raw);
			message.put("headers", str.split("\r\n"));
		} else {

			throw new RuntimeException("Unknown GRPC Frame Type");
		}
	}

	public GRPCMessage(Map<String, Object> json) {
		type = Integer.parseInt(json.get("type").toString());
		message = (Map<String, Object>) json.get("message");
	}

	public byte[] toBytes() throws Exception {
		byte[] bytes = new byte[0];
		if (type == GRPC_WEB_FH_TRAILER) {

			StringJoiner joiner = new StringJoiner("\r\n", "", "\r\n");
			for (String s : (List<String>) message.get("headers")) {

				joiner.add(s);
			}
			bytes = joiner.toString().getBytes();
		} else if (type == GRPC_WEB_FH_DATA) {

			bytes = Protobuf3.encode(JSON.encode(message));
		} else {

			throw new RuntimeException("Unknown GRPC Frame Type");
		}
		ByteArrayOutputStream bio = new ByteArrayOutputStream();
		bio.write(type);
		ByteBuffer bf = ByteBuffer.allocate(4);
		bio.write(bf.putInt(bytes.length).array());
		bio.write(bytes);
		return bio.toByteArray();
	}
}
