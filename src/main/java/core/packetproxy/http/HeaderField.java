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
package packetproxy.http;

import packetproxy.util.PacketProxyUtility;

public class HeaderField {

	private String name, value;

	public HeaderField(String rawLine) {
		String[] fields = rawLine.split(":", 2);
		if (fields.length == 2) {

			name = fields[0].trim();
			value = fields[1].trim();
		} else {

			PacketProxyUtility.getInstance().packetProxyLogErr("invalid header field");
			new Throwable().printStackTrace();
		}
	}

	public HeaderField(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
		return name + ": " + value;
	}

}
