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

import net.arnx.jsonic.JSON;
import packetproxy.common.GRPCMessage;
import packetproxy.http.Http;

import java.util.List;
import java.util.Map;

public class EncodeGRPCWeb extends EncodeHTTPBase
{
	public EncodeGRPCWeb(String ALPN) throws Exception {
		super(ALPN);
	}

    @Override
    protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text+proto")) {
            String base64Body = new String(inputHttp.getBody());
            inputHttp.setBody(JSON.encode(GRPCMessage.decodeMessages(base64Body)).getBytes());
        }
        return inputHttp;
    }

    @Override
    protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text+proto")) {
            List<Map<String, Object>> json = JSON.decode(new String(inputHttp.getBody()));
            inputHttp.setBody(GRPCMessage.encodeMessages(json).getBytes());
        }
        return inputHttp;
    }

    @Override
    protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text")) {
            String base64Body = new String(inputHttp.getBody());
            inputHttp.setBody(JSON.encode(GRPCMessage.decodeMessages(base64Body)).getBytes());
        }
        return inputHttp;
    }

    @Override
    protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text")) {
            List<Map<String, Object>> json = JSON.decode(new String(inputHttp.getBody()));
            inputHttp.setBody(GRPCMessage.encodeMessages(json).getBytes());
        }
        return inputHttp;
    }

    @Override
    public String getName() {
        return "gRPC-Web";
    }
}
