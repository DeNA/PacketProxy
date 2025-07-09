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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import packetproxy.common.AmazonLexV2;
import packetproxy.http.Http;
import packetproxy.util.PacketProxyUtility;

public class EncodeAmazonLexV2 extends EncodeHTTPBase {
	public EncodeAmazonLexV2(String ALPN) throws Exception {
		super(ALPN);
	}

	@Override
	public String getName() {
		return "Amazon LexV2 (Event Streaming)";
	}

	@Override
	protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		return inputHttp;
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		var contentType = inputHttp.getFirstHeader("Content-Type");
		if (!contentType.startsWith("application/vnd.amazon.eventstream")) {
			util.packetProxyLog(
					"[EncodeAmazonLexV2] decodeServerResponseHttp: Content-type is not specified or other content-type detected: "
							+ contentType);
			return inputHttp;
		}
		byte[] body = inputHttp.getBody();
		final int bodyLength = body.length;

		AmazonLexV2 event = AmazonLexV2.fromBytes(body);

		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(event);

		inputHttp.setBody(json.getBytes("UTF-8"));
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		var contentType = inputHttp.getFirstHeader("Content-Type");
		if (!contentType.startsWith("application/vnd.amazon.eventstream")) {
			util.packetProxyLog(
					"[EncodeAmazonLexV2] encodeServerResponseHttp: Content-type is not specified or other content-type detected: "
							+ contentType);
			return inputHttp;
		}

		byte[] body = inputHttp.getBody();
		if (body.length == 0) {
			util.packetProxyLog("[EncodeAmazonLexV2] Warning: Empty body detected, skipping encoding.");
			return inputHttp;
		}

		Gson gson = new GsonBuilder().create();

		AmazonLexV2 lex = gson.fromJson(new String(body, "UTF-8"), AmazonLexV2.class);

		if (lex == null) {
			util.packetProxyLog(
					"[EncodeAmazonLexV2] Warning: Invalid Amazon Lex V2 Event Stream detected, skipping encoding.");
			return inputHttp;
		}

		byte[] encoded = AmazonLexV2.toBytes(lex);

		inputHttp.setBody(encoded);

		return inputHttp;
	}
}
