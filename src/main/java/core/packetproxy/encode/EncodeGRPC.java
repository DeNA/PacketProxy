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

	private byte compressedFlag;
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
		Descriptor type = registry != null ? registry.getInputType(lastGrpcPath) : null;
		if (type != null) {
			inputHttp.setBody(decodeSchemaAwareBody(inputHttp.getBody(), type));
			return inputHttp;
		}
		byte[] raw = inputHttp.getBody();
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {

			compressedFlag = raw[pos];
			if (compressedFlag != 0) {

				throw new Exception("gRPC: compressed flag in gRPC message is not supported yet");
			}
			pos += 1;
			int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos + 4)).getInt();
			pos += 4;
			byte[] grpcMsg = Arrays.copyOfRange(raw, pos, pos + messageLength);
			byte[] decodedMsg = decodeGrpcClientPayload(grpcMsg);
			if (body.size() > 0) {

				body.write("\n".getBytes());
			}
			body.write(Protobuf3.decode(decodedMsg).getBytes(StandardCharsets.UTF_8));
			pos += messageLength;
		}
		inputHttp.setBody(body.toByteArray());
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		lastGrpcPath = inputHttp.getPath();
		Descriptor type = registry != null ? registry.getInputType(lastGrpcPath) : null;
		if (type != null) {
			inputHttp.setBody(encodeSchemaAwareBody(inputHttp.getBody(), type));
			return inputHttp;
		}
		byte[] body = inputHttp.getBody();
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < body.length) {

			byte[] subBody;
			int idx;
			if ((idx = Utils.indexOf(body, pos, body.length, "\n}".getBytes())) > 0) { // split into gRPC messages

				subBody = ArrayUtils.subarray(body, pos, idx + 2);
				pos = idx + 2;
			} else {

				subBody = ArrayUtils.subarray(body, pos, body.length);
				pos = body.length;
			}
			String msg = new String(subBody, StandardCharsets.UTF_8);
			byte[] data = Protobuf3.encode(msg);
			byte[] encodedData = encodeGrpcClientPayload(data);
			int encodedDataLen = encodedData.length;
			rawStream.write((byte) 0); // always compressed flag is zero
			rawStream.write(ByteBuffer.allocate(4).putInt(encodedDataLen).array());
			rawStream.write(encodedData);
		}
		inputHttp.setBody(rawStream.toByteArray());
		return inputHttp;
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] raw = inputHttp.getBody();
		if (raw.length == 0) {

			return inputHttp;
		}
		Descriptor type = registry != null ? registry.getOutputType(lastGrpcPath) : null;
		if (type != null) {
			inputHttp.setBody(decodeSchemaAwareBody(raw, type));
			return inputHttp;
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {

			compressedFlag = raw[pos];
			if (compressedFlag != 0) {

				throw new Exception("gRPC: compressed flag in gRPC message is not supported yet");
			}
			pos += 1;
			int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos + 4)).getInt();
			pos += 4;
			byte[] grpcMsg = Arrays.copyOfRange(raw, pos, pos + messageLength);
			byte[] decodedMsg = decodeGrpcServerPayload(grpcMsg);
			if (body.size() > 0) {

				body.write("\n".getBytes());
			}
			body.write(Protobuf3.decode(decodedMsg).getBytes(StandardCharsets.UTF_8));
			pos += messageLength;
		}
		inputHttp.setBody(body.toByteArray());
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] body = inputHttp.getBody();
		if (body.length == 0) {

			return inputHttp;
		}
		Descriptor type = registry != null ? registry.getOutputType(lastGrpcPath) : null;
		if (type != null) {
			inputHttp.setBody(encodeSchemaAwareBody(body, type));
			return inputHttp;
		}
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < body.length) {

			byte[] subBody;
			int idx;
			if ((idx = Utils.indexOf(body, pos, body.length, "\n}".getBytes())) > 0) { // split into gRPC messages

				subBody = ArrayUtils.subarray(body, pos, idx + 2);
				pos = idx + 2;
			} else {

				subBody = ArrayUtils.subarray(body, pos, body.length);
				pos = body.length;
			}
			String msg = new String(subBody, StandardCharsets.UTF_8);
			byte[] data = Protobuf3.encode(msg);
			byte[] encodedData = encodeGrpcServerPayload(data);
			int encodedDataLen = encodedData.length;
			rawStream.write((byte) 0); // always compressed flag is zero
			rawStream.write(ByteBuffer.allocate(4).putInt(encodedDataLen).array());
			rawStream.write(encodedData);
		}
		inputHttp.setBody(rawStream.toByteArray());
		return inputHttp;
	}

	public byte[] decodeGrpcClientPayload(byte[] payload) throws Exception {
		return payload;
	}

	public byte[] encodeGrpcClientPayload(byte[] payload) throws Exception {
		return payload;
	}

	public byte[] decodeGrpcServerPayload(byte[] payload) throws Exception {
		return payload;
	}

	public byte[] encodeGrpcServerPayload(byte[] payload) throws Exception {
		return payload;
	}

	private byte[] decodeSchemaAwareBody(byte[] raw, Descriptor type) throws Exception {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {
			if (raw[pos] != 0) {
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
			String json;
			try {
				json = JSON_PRINTER.print(DynamicMessage.parseFrom(type, grpcMsg));
			} catch (Exception e) {
				json = Protobuf3.decode(grpcMsg);
			}
			body.write(json.getBytes(StandardCharsets.UTF_8));
		}
		return body.toByteArray();
	}

	private byte[] encodeSchemaAwareBody(byte[] body, Descriptor type) throws Exception {
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
			byte[] data;
			try {
				DynamicMessage.Builder builder = DynamicMessage.newBuilder(type);
				JSON_PARSER.merge(trimmed, builder);
				data = builder.build().toByteArray();
			} catch (Exception e) {
				data = Protobuf3.encode(trimmed);
			}
			rawStream.write(0);
			rawStream.write(ByteBuffer.allocate(4).putInt(data.length).array());
			rawStream.write(data);
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
}
