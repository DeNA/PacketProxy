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
package packetproxy.encode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.arnx.jsonic.JSON;
import packetproxy.common.GRPCMessage;
import packetproxy.http.Http;

public class EncodeGRPCWeb extends EncodeHTTPBase {
	public EncodeGRPCWeb(String ALPN) throws Exception {
		super(ALPN);
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		var contentType = inputHttp.getFirstHeader("Content-Type");
		if (contentType.startsWith("application/grpc-web")) {
			Optional<String> json = Optional.empty();
			if (contentType.endsWith("web-text") || contentType.endsWith("web-text+proto")) {
				var base64Body = new String(inputHttp.getBody());
				json = Optional.of(JSON.encode(GRPCMessage.decodeTextMessages(base64Body)));
			} else if (contentType.endsWith("web") || contentType.endsWith("web+proto")) {
				json = Optional.of(JSON.encode(GRPCMessage.decodeMessages(inputHttp.getBody())));
			}
			json.ifPresent(j -> inputHttp.setBody(j.getBytes()));
		}
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		var contentType = inputHttp.getFirstHeader("Content-Type");
		if (contentType.startsWith("application/grpc-web")) {
			List<Map<String, Object>> json = JSON.decode(new String(inputHttp.getBody()));
			if (contentType.endsWith("web-text") || contentType.endsWith("web-text+proto")) {
				inputHttp.setBody(GRPCMessage.encodeTextMessages(json).getBytes());
			} else if (contentType.endsWith("web") || contentType.endsWith("web+proto")) {
				inputHttp.setBody(GRPCMessage.encodeMessages(json));
			}
		}
		return inputHttp;
	}

	@Override
	protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
		var contentType = inputHttp.getFirstHeader("Content-Type");
		if (contentType.startsWith("application/grpc-web")) {
			Optional<String> json = Optional.empty();
			if (contentType.endsWith("web-text") || contentType.endsWith("web-text+proto")) {
				var base64Body = new String(inputHttp.getBody());
				json = Optional.of(JSON.encode(GRPCMessage.decodeTextMessages(base64Body)));
			} else if (contentType.endsWith("web") || contentType.endsWith("web+proto")) {
				json = Optional.of(JSON.encode(GRPCMessage.decodeMessages(inputHttp.getBody())));
			}
			json.ifPresent(j -> inputHttp.setBody(j.getBytes()));
		}
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		var contentType = inputHttp.getFirstHeader("Content-Type");
		if (contentType.startsWith("application/grpc-web")) {
			List<Map<String, Object>> json = JSON.decode(new String(inputHttp.getBody()));
			if (contentType.endsWith("web-text") || contentType.endsWith("web-text+proto")) {
				inputHttp.setBody(GRPCMessage.encodeTextMessages(json).getBytes());
			} else if (contentType.endsWith("web") || contentType.endsWith("web+proto")) {
				inputHttp.setBody(GRPCMessage.encodeMessages(json));
			}
		}
		return inputHttp;
	}

	@Override
	public String getName() {
		return "gRPC-Web";
	}
}
