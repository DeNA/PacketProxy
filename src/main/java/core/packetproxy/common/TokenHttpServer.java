/*
 * Copyright 2021 DeNA Co., Ltd.
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

import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;

import java.util.HashMap;
import java.util.function.Consumer;

public class TokenHttpServer extends NanoHTTPD {

    private Consumer<String> onReceived;

    private class Token {
        public String token;
    }

    public TokenHttpServer(String hostname, int port, Consumer<String> onReceived) {
        super(hostname, port);
        this.onReceived = onReceived;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        if (method.equals(Method.OPTIONS) && uri.equals("/token")){
            Response res = NanoHTTPD.newFixedLengthResponse( Response.Status.OK, MIME_HTML, null);
            res.addHeader("Access-Control-Allow-Origin", "*");
            res.addHeader("Access-Control-Allow-Headers", "Content-Type");
            res.addHeader("Access-Control-Allow-Methods", "POST,OPTIONS");
            res.addHeader("Access-Control-Max-Age", "86400");
            res.addHeader("Access-Control-Allow-Private-Network", "true");
            return res;
        }

        if (method.equals(Method.POST) && uri.equals("/token")){
            try {
                HashMap<String, String> map = new HashMap<String, String>();
                session.parseBody(map);
                String json = map.get("postData");
                Token token = new Gson().fromJson(json, Token.class);

                onReceived.accept(token.token);

                Response res = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"ok\"}");
                res.addHeader("Access-Control-Allow-Origin", "*");
                return res;

            } catch (Exception e) {
                return NanoHTTPD.newFixedLengthResponse( Response.Status.INTERNAL_ERROR, MIME_HTML, null);
            }
        }

        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, null);
    }

}
