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

import java.nio.ByteBuffer;
import java.util.Arrays;

import packetproxy.common.Protobuf3;
import packetproxy.http.Http;
import packetproxy.http2.Grpc;

public class EncodeGRPC extends EncodeHTTPBase
{

	private byte compressedFlag;

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
		compressedFlag = raw[0];
		int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, 1, 5)).getInt();
		byte[] byteBasedMsg = Arrays.copyOfRange(raw, 5, 5+messageLength);
		String json = Protobuf3.decode(byteBasedMsg);
		inputHttp.setBody(json.getBytes("UTF-8"));
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		byte[] encodeBytes = Protobuf3.encode(new String(inputHttp.getBody(),"UTF-8"));
		int messageLength = encodeBytes.length;
		byte[] raw = new byte[5+messageLength];

		for(int i=0;i<encodeBytes.length;++i){
			raw[5+i] = encodeBytes[i];
		}

		byte[] msgLength = ByteBuffer.allocate(4).putInt(messageLength).array();
		raw[1] = msgLength[0];
		raw[2] = msgLength[1];
		raw[3] = msgLength[2];
		raw[4] = msgLength[3];

		raw[0] = compressedFlag;

		inputHttp.setBody(raw);
		return inputHttp;
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] raw = inputHttp.getBody();
		if (raw.length == 0) {
			return inputHttp;
		}
		compressedFlag = raw[0];
		int messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, 1, 5)).getInt();
		byte[] byteBasedMsg = Arrays.copyOfRange(raw, 5, 5+messageLength);
		String json = Protobuf3.decode(byteBasedMsg);
		inputHttp.setBody(json.getBytes("UTF-8"));
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		if (inputHttp.getBody().length == 0) {
			return inputHttp;
		}
		byte[] encodeBytes = Protobuf3.encode(new String(inputHttp.getBody(),"UTF-8"));
		int messageLength = encodeBytes.length;
		byte[] raw = new byte[5+messageLength];

		for(int i=0;i<encodeBytes.length;++i){
			raw[5+i] = encodeBytes[i];
		}

		byte[] msgLength = ByteBuffer.allocate(4).putInt(messageLength).array();
		raw[1] = msgLength[0];
		raw[2] = msgLength[1];
		raw[3] = msgLength[2];
		raw[4] = msgLength[3];

		raw[0] = compressedFlag;

		inputHttp.setBody(raw);
		return inputHttp;
	}
}
