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

import java.util.Map;
import net.arnx.jsonic.JSON;
import packetproxy.model.Packet;

public class EncodeFirebase extends EncodeHTTPWebSocket {

	public EncodeFirebase(String ALPN) throws Exception {
		super(ALPN);
	}

	@Override
	public String getName() {
		return "FirebaseDB";
	}

	@Override
	public String getSummarizedRequest(Packet packet) {
		byte[] raw_data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
		String data = new String(raw_data);
		try {

			Map<String, Map<String, Object>> json = JSON.decode(data);
			String a = json.get("d").get("a").toString();

			String action = "UNKNOWN";
			switch (a) {

				case "n" :
					action = "DELETE";
					break;
				case "q" :
					action = "READ";
					break;
				case "p" :
					action = "WRITE";
					break;
			}

			String id = json.get("d").get("r").toString();
			Map<String, Object> b = (Map<String, Object>) json.get("d").get("b");

			if (a.equals("auth")) {

				return id + "LOGIN BY" + b.get("cred");
			}

			String path = b.get("p").toString();

			return String.join(" ", id, action, path);
		} catch (Exception e) {

			return data;
		}
	}

	@Override
	public String getSummarizedResponse(Packet packet) {
		byte[] raw_data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
		String data = new String(raw_data);
		try {

			Map<String, Map<String, Object>> json = JSON.decode(data);
			Map<String, Object> d = (Map<String, Object>) json.get("d");
			if (d.containsKey("r")) {

				return d.get("r").toString() + d.get("b").toString();
			}
			Map<String, Object> b = (Map<String, Object>) json.get("d").get("b");
			String path = b.get("p").toString();
			if (path == null)
				path = "";
			return "FETCHED: " + path;
		} catch (Exception e) {

			return data;
		}
	}

}
