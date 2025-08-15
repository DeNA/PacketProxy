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
package packetproxy.util;
import static packetproxy.util.Logging.errWithStackTrace;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;

public class PacketProxyUtility {

	private static String OS = System.getProperty("os.name").toLowerCase();
	private static PacketProxyUtility instance = null;

	public static PacketProxyUtility getInstance() {
		if (instance == null) {
			instance = new PacketProxyUtility();
		}
		return instance;
	}

	public PacketProxyUtility() {
	}

	public byte[] prettyFormatJSONInRawData(byte[] data) {
		try {
			String str = new String(data, "UTF-8");
			Stream<String> stream = Arrays.asList(str.split("\r\n\r\n")).stream();
			return stream.map(this::prettyFormatJSON).filter(j -> !j.isEmpty()).collect(Collectors.joining("\n"))
					.getBytes();
		} catch (UnsupportedEncodingException e) {
			errWithStackTrace(e);
			return "convert failed".getBytes();
		}
	}

	public String prettyFormatJSON(String data) {
		try {
			JSONObject tmp_obj;
			boolean begin_with_left_square_bracket = false; // This variable is true, if json string begin with [
			int begin = data.length();
			int end = data.length();
			if (data.contains("{"))
				begin = Math.min(begin, data.indexOf('{'));
			if (data.contains("["))
				begin = Math.min(begin, data.indexOf('['));
			data = data.substring(begin, end);
			if (data.isEmpty())
				return "";
			if (0 == data.indexOf('[')) {
				data = String.format("{data:%s}", data);
				begin_with_left_square_bracket = true;
			}
			tmp_obj = new JSONObject(data);
			if (begin_with_left_square_bracket) {
				return ((JSONArray) tmp_obj.get("data")).toString(2);
			}
			return tmp_obj.toString(2);
		} catch (Exception e) {
			return "";
		}
	}

	public boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public boolean isMac() {
		return (OS.indexOf("mac") >= 0);
	}

	public boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
	}

	public boolean isBinaryData(byte[] data, int defaultSize) {
		int cnt = 0;
		for (int i = 0; i < Math.min(data.length, defaultSize); i++) {
			if (data[i] == 0x09 || data[i] == 0x0a || data[i] == 0x0d) {
				continue;
			}
			if ((0x00 <= data[i] && data[i] < 0x20) || data[i] == 0x7f) {
				cnt++;
			}
		}
		return cnt > 30;
	}
}
