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

import static packetproxy.util.Logging.errWithStackTrace;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;

public class StringUtils {

	public static String randomUUID() {
		/* UUID sample: a3faf181-996a-457d-831f-18767419788c */
		StringBuilder sb = new StringBuilder();
		sb.append(RandomStringUtils.random(8, "0123456789abcdef"));
		sb.append("-");
		sb.append(RandomStringUtils.random(4, "0123456789abcdef"));
		sb.append("-");
		sb.append(RandomStringUtils.random(4, "0123456789abcdef"));
		sb.append("-");
		sb.append(RandomStringUtils.random(4, "0123456789abcdef"));
		sb.append("-");
		sb.append(RandomStringUtils.random(12, "0123456789abcdef"));
		return sb.toString();
	}

	public static byte[] prettyUpJson(byte[] json) {
		try {

			return prettyUpJson(new String(json)).getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {

			errWithStackTrace(e);
		}
		return prettyUpJson(new String(json)).getBytes();
	}

	public static String prettyUpJson(String json) {
		Map<?, ?> jsonMap = (Map<?, ?>) JSON.decode(json);
		JSON encoder = new JSON();
		encoder.setPrettyPrint(true);
		encoder.setInitialIndent(0);
		encoder.setIndentText("    ");
		encoder.format(jsonMap);
		return encoder.format(jsonMap).replace("\\u003C", "<").replace("\\u003E", ">");
	}

	public static byte[] minifyJson(byte[] json) {
		return minifyJson(new String(json)).getBytes();
	}

	public static String minifyJson(String json) {
		Map<?, ?> jsonMap = (Map<?, ?>) JSON.decode(json);
		return JSON.encode(jsonMap);
	}

	public static int countChar(String s, char c, int start_idx, int end_idx) {
		int count = 0;
		end_idx = Math.min(s.length(), end_idx);
		for (int i = start_idx; i < end_idx; i++) {

			if (s.charAt(i) == c)
				count++;
		}
		return count;
	}

	public static byte[] hexToByte(byte[] hexa) throws Exception {
		String hex = new String(hexa).trim();
		// assert(hex.length() % 2 == 0);
		if (hex.length() % 2 != 0) {

			throw new Exception(I18nString.get("Length of string is not multiples of 2"));
		}

		// Logging.log(hex);
		byte[] bytes = new byte[hex.length() / 2];
		for (int index = 0; index < bytes.length; index++) {

			bytes[index] = (byte) Integer.parseInt(hex.substring(index * 2, (index + 1) * 2), 16);
		}
		return bytes;
	}

	public static String byteToHex(byte bytes[]) {
		// バイト配列の２倍の長さの文字列バッファを生成。
		StringBuffer strbuf = new StringBuffer(bytes.length * 2);

		// バイト配列の要素数分、処理を繰り返す。
		for (int index = 0; index < bytes.length; index++) {

			// バイト値を自然数に変換。
			int bt = bytes[index] & 0xff;

			// バイト値が0x10以下か判定。
			if (bt < 0x10) {

				// 0x10以下の場合、文字列バッファに0を追加。
				strbuf.append("0");
			}

			// バイト値を16進数の文字列に変換して、文字列バッファに追加。
			strbuf.append(Integer.toHexString(bt));
		}

		/// 16進数の文字列を返す。
		return strbuf.toString();
	}

	public static byte[] intToByte(int v, boolean littleEndian) {
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i++) {

			bytes[i] = (byte) (0xff & (v >> (i * 8)));
		}
		if (!littleEndian) {

			ArrayUtils.reverse(bytes);
		}
		return bytes;
	}

	public static String intToHex(int v, boolean littleEndian) {
		byte[] bytes = intToByte(v, littleEndian);
		return byteToHex(bytes);
	}

	/**
	 * バイナリを壊さないでパターンマッチングをする人です。疑似的に実現しているので、ascii以外は0x01にマッピングしてから比較しているのでマルチバイトの検索はできません
	 *
	 * @param input
	 * @param regex
	 * @param replace
	 * @return
	 */
	public static byte[] pseudoBinaryPatternReplace(byte[] input, String regex, String replace) {
		byte[] input2 = input.clone();
		for (int i = 0; i < input2.length; ++i) {

			if (input2[i] < 0x00) {

				input2[i] = 0x01;
			}
		}
		String pseudoString = new String(input2);
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(pseudoString);
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		if (matcher.find()) {

			String matched = matcher.group(0);
			int from = pseudoString.indexOf(matched);
			// Logging.err("Match! : " + pseudoString.length() + " : " + from + " : "
			// + regex + " : " + pseudoString);
			byte[] tmp_result = new byte[matched.length()];
			for (int i = 0; i < matched.length(); ++i) {

				tmp_result[i] = input[from + i];
			}
			result.write(input, 0, from);
			// Logging.err(result);
			result.write(replace.getBytes(), 0, replace.getBytes().length);
			// Logging.err(result);
			result.write(input, from + matched.length(), input.length - (from + matched.length()));
			// Logging.err(result);
		}
		return result.toByteArray();
	}

	public static int binaryFind(byte[] input, byte[] pattern) {
		return binaryFind(input, pattern, 0);
	}

	/**
	 * inputからpatternで指定されたバイト列が見つかった最初の位置を返す
	 *
	 * @param input
	 * @param pattern
	 * @param fromIndex
	 * @return
	 */
	public static int binaryFind(byte[] input, byte[] pattern, int fromIndex) {
		BoyerMoore bm = new BoyerMoore(pattern);
		int idx;
		return (idx = bm.searchIn(input, fromIndex)) < 0 ? idx : idx + fromIndex;
	}

	/**
	 * inputからpattenで指定されたバイト列が見つかった場合にreplaceに置き換える patternとreplaceは同じ長さでなければならない
	 *
	 * @param input
	 * @param pattern
	 * @param replace
	 * @return
	 */
	public static byte[] binaryReplace(byte[] input, byte[] pattern, byte[] replace) throws Exception {
		if (pattern.length != replace.length) {

			throw new Exception(I18nString.get("Lengths of target and replacement are not same."));
		}

		byte[] result = input.clone();
		if (pattern.length == 0) {

			return result;
		}
		int start = 0;
		while ((start = binaryFind(input, pattern, start)) > 0) {

			// Logging.log("Replace : " + input.length + " : " + start + " : " + new
			// String(byteToHex(pattern)) + " -> " + new String(byteToHex(replace)));
			for (int j = 0; j < replace.length; j++) {

				result[start + j] = replace[j];
			}
			start = start + pattern.length - 1;
		}
		return result;
	}

	/**
	 * @param input
	 * @param hexPattern
	 * @param hexReplace
	 * @return
	 */
	public static byte[] hexBinaryReplace(byte[] input, String hexPattern, String hexReplace) throws Exception {
		byte[] pattern = hexToByte(hexPattern.getBytes());
		byte[] replace = hexToByte(hexReplace.getBytes());
		return binaryReplace(input, pattern, replace);
	}

	public static byte[] toAscii(byte[] data) {
		byte[] ret = data.clone();
		for (int i = 0; i < ret.length; i++) {

			if (ret[i] < 0x20 || 0xff <= ret[i]) {

				ret[i] = '.';
			}
		}
		return ret;
	}

	public static boolean validatePrintableUTF8(byte[] data) {
		for (int i = 0; i < data.length; i++) {

			byte octet = data[i];
			if (octet == 0x0D || octet == 0x0A) {

				continue;
			}
			if (0 < octet && octet < 0x20) {

				return false;
			}
			if (octet == 0x7F) {

				return false;
			}
			if ((octet & 0x80) == 0) {

				continue;
			}
			int end = 0;
			if ((octet & 0xE0) == 0xC0) {

				end = i + 1;
			} else if ((octet & 0xF0) == 0xE0) {

				end = i + 2;
			} else if ((octet & 0xF8) == 0xF0) {

				end = i + 3;
			} else {
				/* Java supports BMP only */

				return false;
			}
			while (i < end) {

				i++;
				octet = data[i];
				if ((octet & 0xC0) != 0x80) {

					return false;
				}
			}
		}
		return true;
	}
}
