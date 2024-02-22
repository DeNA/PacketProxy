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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Protobuf3;
import packetproxy.common.Utils;
import packetproxy.http.Http;
import packetproxy.http2.Grpc;

public class EncodeGRPC extends EncodeHTTPBase
{

	private byte compressedFlag;

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
		byte[] raw = inputHttp.getBody();
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {
			compressedFlag = raw[pos];
			if (compressedFlag != 0) {
				throw new Exception("gRPC: compressed flag in gRPC message is not supported yet");
			}
			pos += 1;
			int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos+4)).getInt();
			pos += 4;
			byte[] grpcMsg = Arrays.copyOfRange(raw, pos, pos+messageLength);
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
		byte[] body = inputHttp.getBody();
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < body.length) {
			int idx;
			if ((idx = Utils.indexOf(body, pos, body.length, "\n}".getBytes())) > 0) { // split into gRPC messages
				byte[] msgBytes = ArrayUtils.subarray(body, pos, idx+2);
				String msg = new String(msgBytes, StandardCharsets.UTF_8);
				byte[] data = Protobuf3.encode(msg);
				byte[] encodedData = encodeGrpcClientPayload(data);
				int encodedDataLen = encodedData.length;
				rawStream.write((byte)0); // always compressed flag is zero
				rawStream.write(ByteBuffer.allocate(4).putInt(encodedDataLen).array());
				rawStream.write(encodedData);
				pos = idx+2;
			} else {
				break;
			}
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
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < raw.length) {
			compressedFlag = raw[pos];
			if (compressedFlag != 0) {
				throw new Exception("gRPC: compressed flag in gRPC message is not supported yet");
			}
			pos += 1;
			int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos+4)).getInt();
			pos += 4;
			byte[] grpcMsg = Arrays.copyOfRange(raw, pos, pos+messageLength);
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
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		int pos = 0;
		while (pos < body.length) {
			int idx;
			if ((idx = Utils.indexOf(body, pos, body.length, "\n}".getBytes())) > 0) { // split into gRPC messages
				byte[] msgBytes = ArrayUtils.subarray(body, pos, idx+2);
				String msg = new String(msgBytes, StandardCharsets.UTF_8);
				byte[] data = Protobuf3.encode(msg);
				byte[] encodedData = encodeGrpcServerPayload(data);
				int encodedDataLen = encodedData.length;
				rawStream.write((byte)0); // always compressed flag is zero
				rawStream.write(ByteBuffer.allocate(4).putInt(encodedDataLen).array());
				rawStream.write(encodedData);
				pos = idx+2;
			} else {
				break;
			}
		}
		inputHttp.setBody(rawStream.toByteArray());
		return inputHttp;
	}

	public byte[] decodeGrpcClientPayload(byte[] payload) throws Exception { return payload; }
	public byte[] encodeGrpcClientPayload(byte[] payload) throws Exception { return payload; }
	public byte[] decodeGrpcServerPayload(byte[] payload) throws Exception { return payload; }
	public byte[] encodeGrpcServerPayload(byte[] payload) throws Exception { return payload; }
}
