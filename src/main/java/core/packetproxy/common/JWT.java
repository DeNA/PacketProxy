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
package packetproxy.common;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import net.arnx.jsonic.JSON;

import packetproxy.util.PacketProxyUtility;

public class JWT
{
	protected String header;
	protected String payload;

	/*
	public static void main(String[] args) {
		try {
			JWT jwt = new JWT("{ header: { a: \"hello\" }, payload: { b: \"world\", c: { d: \"hoge\" } } }");
			PacketProxyUtility.getInstance().packetProxyLog(jwt.getPayloadValue("c/d"));
			jwt.setHeaderValue("jwk/kid", "abc");
			jwt.setHeaderValue("c", "ccc");
			jwt.setPayloadValue("c/d","fuga");
			PacketProxyUtility.getInstance().packetProxyLog(jwt.toJwtString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	*/

	public JWT() {
	}
	public JWT(JWT jwt) {
		this.header = jwt.header;
		this.payload = jwt.payload;
	}
	public JWT(String jwtString) throws Exception {
		ByteArrayInputStream bis = new ByteArrayInputStream(jwtString.getBytes());
		Map<String,Object> map = JSON.decode(bis);
		header = JSON.encode(map.get("header"));
		payload = JSON.encode(map.get("payload"));
	}
	public String getHeaderValue(String keystr) { // keystrは、aaa/bbb/cccというフォーマットで指定可能
		return getValue(header, keystr);
	}
	public String getPayloadValue(String keystr) { // keystrは、aaa/bbb/cccというフォーマットで指定可能
		return getValue(payload, keystr);
	}
	public void setHeaderValue(String keystr, String value) { // keystrは、aaa/bbb/cccというフォーマットで指定可能
		header = setValue(header, keystr, value);
	}
	public void setPayloadValue(String keystr, String value) { // keystrは、aaa/bbb/cccというフォーマットで指定可能
		payload = setValue(payload, keystr, value);
	}
	public void debug() {
		PacketProxyUtility.getInstance().packetProxyLog(header);
		PacketProxyUtility.getInstance().packetProxyLog(payload);
	}
	public String toJwtString() throws Exception {
		return String.format("{\n  header: %s,\n  payload: %s\n}", createHeader(header), createPayload(payload));
	}
	protected String createSignature(String input) throws Exception {
		return "NotDefined";
	}
	protected String createHeader(String input) throws Exception {
		return input;
	}
	protected String createPayload(String input) throws Exception {
		return input;
	}
	private String getValue(String chunk, String keystr) {
		String[] keys = keystr.split("/");
		Map<String,Object> cur = new JSON().parse(chunk);
		for (int i = 0; i < keys.length-1; i++) {
			if (cur == null)
				return null;
			cur = forceCast(cur.get(keys[i]));
		}
		if (cur == null)
			return null;
		return forceCast(cur.get(keys[keys.length-1]));
	}
	private String setValue(String chunk, String keystr, String value) {
		String[] keys = keystr.split("/");
		Map<String,Object> cur = new JSON().parse(chunk);
		Map<String,Object> root = cur;
		for (int i = 0; i < keys.length-1; i++) {
			if (!cur.containsKey(keys[i])) {
				cur.put(keys[i], new HashMap<String, Object>());
			}
			cur = forceCast(cur.get(keys[i]));
		}
		cur.put(keys[keys.length-1], value);
		return JSON.encode(root);
	}
	@SuppressWarnings("unchecked")
	private static <T> T forceCast(Object src) {
		T castedObject = (T) src;
		return castedObject;
	}
}
