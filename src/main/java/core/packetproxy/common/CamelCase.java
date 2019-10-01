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

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

import packetproxy.util.PacketProxyUtility;

public class CamelCase {
	
	/*
	static public void main(String[] args) {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		try {
			util.packetProxyLog(CamelCase.toCamelCase("1453"));
			util.packetProxyLog(CamelCase.toCamelCase("a"));
			util.packetProxyLog(CamelCase.toCamelCase("test"));
			util.packetProxyLog(CamelCase.toCamelCase("abc_def_ghf"));
			util.packetProxyLog(CamelCase.toCamelCase("abc-def-ghf"));
			util.packetProxyLog(CamelCase.toCamelCase("abc_def-ghf"));
			util.packetProxyLog(CamelCase.toCamelCase("abc%def$-ghf"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	*/
	
	static public String toCamelCase(String s) {
		Pattern pattern = Pattern.compile("([a-zA-Z][a-zA-Z0-9]*)");
		Matcher matcher = pattern.matcher(s);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, toProperCase(matcher.group()));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	static private String toProperCase(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
}
