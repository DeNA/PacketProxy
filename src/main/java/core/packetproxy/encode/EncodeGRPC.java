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
package packetproxy.encode;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Protobuf3;
import packetproxy.common.Utils;
import packetproxy.grpc.GrpcServiceRegistry;
import packetproxy.grpc.GrpcServiceRegistryStore;
import packetproxy.http.Http;
import packetproxy.http2.Grpc;
import packetproxy.util.Logging;

public class EncodeGRPC extends EncodeHTTPBase {

	private static final JsonFormat.Printer JSON_PRINTER =
		JsonFormat.printer().preservingProtoFieldNames().alwaysPrintFieldsWithNoPresence();

	private static final JsonFormat.Parser JSON_PARSER = JsonFormat.parser().ignoringUnknownFields();

	private volatile GrpcServiceRegistry registry;
	private volatile String lastGrpcPath;

	public synchronized void setDescriptorFile(File descFile) {
		if (descFile == null || !descFile.isFile()) {
			registry = null;
			return;
		}
		try {
			registry = GrpcServiceRegistryStore.getInstance().get(descFile);
		} catch (Exception e) {
			Logging.errWithStackTrace(e);
			registry = null;
		}
	}

	public EncodeGRPC() throws Exception {
		super();
	}

	public EncodeGRPC(String ALPN) throws Exception {
		super(ALPN, new Grpc());
	}

	@Override
	public String getName() {
		return "gRPC";
	}

	@Override
	protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
		lastGrpcPath = inputHttp.getPath();
		Descriptor type = getInputType(lastGrpcPath);
		inputHttp.setBody(decodeLengthPrefixedBody(inputHttp.getBody(), type));
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		lastGrpcPath = inputHttp.getPath();
		Descriptor type = getInputType(lastGrpcPath);
		inputHttp.setBody(encodeLengthPrefixedFromUtf8Json(inputHttp.getBody(), type));
		return inputHttp;
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] raw = inputHttp.getBody();
		if (raw.length == 0) {
			return inputHttp;
		}
		Descriptor type = getOutputType(lastGrpcPath);
		inputHttp.setBody(decodeLengthPrefixedBody(raw, type));
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] body = inputHttp.getBody();
		if (body.length == 0) {
			return inputHttp;
		}
		Descriptor type = getOutputType(lastGrpcPath);
		inputHttp.setBody(encodeLengthPrefixedFromUtf8Json(body, type));
		return inputHttp;
	}

	private Descriptor getInputType(String grpcPath) {
		if (registry == null) {
			return null;
		}
		return registry.getInputType(grpcPath);
	}

	private Descriptor getOutputType(String lastRequestGrpcPath) {
		if (registry == null) {
			return null;
		}
		return registry.getOutputType(lastRequestGrpcPath);
	}

	private byte[] decodeLengthPrefixedBody(byte[] raw, Descriptor type) throws Exception {
		if (type == null) {
			return decodeSchemalessGrpcBody(raw);
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {
			byte compressedFlag = raw[pos];
			if (compressedFlag != 0) {
				throw new Exception("gRPC: compressed flag in gRPC message is not supported yet");
			}
			pos += 1;
			int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos + 4)).getInt();
			pos += 4;
			byte[] grpcMsg = Arrays.copyOfRange(raw, pos, pos + messageLength);
			pos += messageLength;
			if (body.size() > 0) {
				body.write('\n');
			}
			body.write(decodeOnePayloadToUtf8String(grpcMsg, type).getBytes(StandardCharsets.UTF_8));
		}
		return body.toByteArray();
	}

	private byte[] decodeSchemalessGrpcBody(byte[] raw) throws Exception {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {
			byte compressedFlag = raw[pos];
			if (compressedFlag != 0) {
				throw new Exception("gRPC: compressed flag in gRPC message is not supported yet");
			}
			pos += 1;
			int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos + 4)).getInt();
			pos += 4;
			byte[] grpcMsg = Arrays.copyOfRange(raw, pos, pos + messageLength);
			pos += messageLength;
			if (body.size() > 0) {
				body.write('\n');
			}
			body.write(Protobuf3.decode(grpcMsg).getBytes(StandardCharsets.UTF_8));
		}
		return body.toByteArray();
	}

	private String decodeOnePayloadToUtf8String(byte[] payload, Descriptor type) throws Exception {
		try {
			DynamicMessage msg = DynamicMessage.parseFrom(type, payload);
			return JSON_PRINTER.print(msg);
		} catch (Exception e) {
			return Protobuf3.decode(payload);
		}
	}

	private byte[] encodeLengthPrefixedFromUtf8Json(byte[] body, Descriptor type) throws Exception {
		if (type == null) {
			return encodeSchemalessGrpcBody(body);
		}
		return encodeBodyFromJsonChunksForSchema(body, type);
	}

	private byte[] encodeSchemalessGrpcBody(byte[] body) throws Exception {
		if (body.length == 0) {
			return body;
		}
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < body.length) {
			byte[] subBody;
			int idx = Utils.indexOf(body, pos, body.length, "\n}".getBytes(StandardCharsets.UTF_8));
			if (idx > 0) {
				subBody = ArrayUtils.subarray(body, pos, idx + 2);
				pos = idx + 2;
			} else {
				subBody = ArrayUtils.subarray(body, pos, body.length);
				pos = body.length;
			}
			String msg = new String(subBody, StandardCharsets.UTF_8);
			byte[] data = Protobuf3.encode(msg);
			writeGrpcFrame(rawStream, data);
		}
		return rawStream.toByteArray();
	}

	private List<String> splitTopLevelJsonObjects(String text) {
		if (text == null || text.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		JsonFactory factory = new JsonFactory();
		try (com.fasterxml.jackson.core.JsonParser p = factory.createParser(text)) {
			int depth = 0;
			int start = -1;
			while (p.nextToken() != null) {
				if (p.currentToken() == JsonToken.START_OBJECT) {
					if (depth == 0) {
						start = (int) p.getCurrentLocation().getCharOffset();
					}
					depth++;
				} else if (p.currentToken() == JsonToken.END_OBJECT) {
					depth--;
					if (depth == 0 && start >= 0) {
						int end = (int) p.getCurrentLocation().getCharOffset() + 1;
						out.add(text.substring(start, end));
					}
				}
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	private byte[] encodeBodyFromJsonChunksForSchema(byte[] body, Descriptor type) throws Exception {
		String s = new String(body, StandardCharsets.UTF_8);
		List<String> objects = splitTopLevelJsonObjects(s);
		if (objects.isEmpty() && !s.trim().isEmpty()) {
			objects = Collections.singletonList(s);
		}
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		for (String json : objects) {
			String trimmed = json.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			byte[] data = encodeOneJsonToBinary(trimmed, type);
			writeGrpcFrame(rawStream, data);
		}
		return rawStream.toByteArray();
	}

	private byte[] encodeOneJsonToBinary(String json, Descriptor type) throws Exception {
		try {
			DynamicMessage.Builder builder = DynamicMessage.newBuilder(type);
			JSON_PARSER.merge(json, builder);
			return builder.build().toByteArray();
		} catch (Exception e) {
			return Protobuf3.encode(json);
		}
	}

	private void writeGrpcFrame(ByteArrayOutputStream rawStream, byte[] payload) throws Exception {
		rawStream.write(0);
		rawStream.write(ByteBuffer.allocate(4).putInt(payload.length).array());
		rawStream.write(payload);
	}
}
