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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.util.Map;
import packetproxy.http.Http;

public class EncodeCBOR extends EncodeHTTPBase {

	private ObjectMapper cborMapper;
	private ObjectMapper jsonMapper;

	public EncodeCBOR(String ALPN) throws Exception {
		super(ALPN);
		CBORFactory f = new CBORFactory();
		cborMapper = new ObjectMapper(f);
		jsonMapper = new ObjectMapper();
	}

	private byte[] ObjToAltObj(byte[] src, ObjectMapper srcObjMapper, ObjectMapper dstObjMapper) {
		try {

			Map<String, Object> objMap = srcObjMapper.readValue(src, new TypeReference<Map<String, Object>>() {
			});
			return dstObjMapper.writeValueAsBytes(objMap);

		} catch (Exception e) {

			e.printStackTrace();
		}
		return new byte[0];
	}

	private byte[] cborToJson(byte[] src) {
		return ObjToAltObj(src, cborMapper, jsonMapper);
	}

	private byte[] jsonToCbor(byte[] src) {
		return ObjToAltObj(src, jsonMapper, cborMapper);
	}

	@Override
	public String getName() {
		return "CBOR over HTTP";
	}

	@Override
	protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
		inputHttp.setBody(cborToJson(inputHttp.getBody()));
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		inputHttp.setBody(jsonToCbor(inputHttp.getBody()));
		return inputHttp;
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		inputHttp.setBody(cborToJson(inputHttp.getBody()));
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		inputHttp.setBody(jsonToCbor(inputHttp.getBody()));
		return inputHttp;
	}
}
