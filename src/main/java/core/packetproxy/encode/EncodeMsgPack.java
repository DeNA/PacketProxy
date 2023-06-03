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
import org.msgpack.jackson.dataformat.MessagePackFactory;
import packetproxy.http.Http;

import java.util.List;
import java.util.Map;

public class EncodeMsgPack extends EncodeHTTPBase {

    private ObjectMapper msgPackMapper;
    private ObjectMapper jsonMapper;

	public EncodeMsgPack(String ALPN) throws Exception {
		super(ALPN);
        MessagePackFactory f = new MessagePackFactory();
        msgPackMapper = new ObjectMapper(f);
        jsonMapper = new ObjectMapper();
	}

    private byte[] msgPackToJson(byte[] src) throws Exception {
        if (0x90 <= (src[0] & 0xff) && (src[0] & 0xff) <= 0x9f) { // fixarray
            List<Object> objList = this.msgPackMapper.readValue(src, new TypeReference<List<Object>>(){});
            return this.jsonMapper.writeValueAsBytes(objList);
        } else if ((src[0] & 0xff) == 0xdc || (src[0] & 0xff) == 0xdd) { // array16, array32
            List<Object> objList = this.msgPackMapper.readValue(src, new TypeReference<List<Object>>(){});
            return this.jsonMapper.writeValueAsBytes(objList);
        } else if (0x80 <= (src[0] & 0xff) && (src[0] & 0xff) <= 0x8f) { // fixmap
            Map<String, Object> objMap = this.msgPackMapper.readValue(src, new TypeReference<Map<String,Object>>(){});
            return this.jsonMapper.writeValueAsBytes(objMap);
        } else if ((src[0] & 0xff) == 0xde || (src[0] & 0xff) == 0xdf) { // map16, map32
            Map<String, Object> objMap = this.msgPackMapper.readValue(src, new TypeReference<Map<String,Object>>(){});
            return this.jsonMapper.writeValueAsBytes(objMap);
        } else {
            throw new Exception("EncodeMsgPack: Root of msgpack data should be array or map.");
        }
    }

    private byte[] jsonToMsgPack(byte[] src) throws Exception {
        if (src[0] == '[') {
            List<Object> objList = this.jsonMapper.readValue(src, new TypeReference<List<Object>>(){});
            return this.msgPackMapper.writeValueAsBytes(objList);
        } else if (src[0] == '{') {
            Map<String, Object> objMap = this.jsonMapper.readValue(src, new TypeReference<Map<String,Object>>(){});
            return this.msgPackMapper.writeValueAsBytes(objMap);
        } else {
            throw new Exception("EncodeMsgPack: Json data should be start with '{' or '['.");
        }
    }

    @Override
    public String getName() {
        return "MessagePack over HTTP";
    }

    @Override
    protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(msgPackToJson(inputHttp.getBody()));
        return inputHttp;
    }

    @Override
    protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(jsonToMsgPack(inputHttp.getBody()));
        return inputHttp;
    }

    @Override
    protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(msgPackToJson(inputHttp.getBody()));
        return inputHttp;
    }

    @Override
    protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
        inputHttp.setBody(jsonToMsgPack(inputHttp.getBody()));
        return inputHttp;
    }
}