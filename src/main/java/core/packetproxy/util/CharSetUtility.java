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

import static packetproxy.util.Logging.log;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import packetproxy.http.HttpHeader;
import packetproxy.model.CharSet;
import packetproxy.model.CharSets;

public class CharSetUtility {

	private static CharSetUtility instance = null;
	private static String DEFAULT_CHARSET = "UTF-8";
	private static String AUTO_CHARSET = "AUTO";
	private String charSet = DEFAULT_CHARSET;
	private boolean isAuto = false;

	public static CharSetUtility getInstance() {
		if (null == instance) {

			instance = new CharSetUtility();
			if (!instance.getAvailableCharSetList().contains(DEFAULT_CHARSET)) {

				instance.charSet = instance.getAvailableCharSetList().get(0);
			}
		}
		return instance;
	}

	private Object[] parseDocWithToken(String data, String[] startToken, String[] endToken, int cur, boolean allowEOL) {
		int s = data.length();
		int s_index = -1;
		int e_index = -1;
		for (int t = 0; t < startToken.length; t++) {

			int s_ = data.indexOf(startToken[t], cur);
			if (-1 == s_) {

				continue;
			}
			if (s_ < s) {

				s = s_;
				s_index = t;
			}
		}
		if (data.length() == s) {

			return new Object[]{"", -1};
		}
		cur = s + startToken[s_index].length();
		int e = data.length();
		for (int t = 0; t < endToken.length; t++) {

			int e_ = data.indexOf(endToken[t], cur);
			if (-1 == e_) {

				continue;
			}
			if (e_ < e) {

				e = e_;
				e_index = t;
			}
		}
		if (data.length() == e && !allowEOL) {

			return new Object[]{"", -1};
		}
		String metaData = data.substring(s + startToken[s_index].length(), e);
		if ((0 <= e_index) && (e_index <= endToken.length)) {

			e += endToken[e_index].length();
		}
		return new Object[]{metaData, e};
	}

	public String guessCharSetFromMetatag(byte[] rawData) {
		String data = "";
		String[] startKeywords = new String[]{"<meta", "&lt;meta"};
		String[] endKeywords = new String[]{"/>", "</meta>", ">", "&#47;&gt;", "&lt;&#47;meta&gt;", "&gt;"};
		String[] charsetStartKeywords = new String[]{"charset=\"", "charset=\'"};
		String[] charsetEndKeywords = new String[]{"\"", "\'"};
		String[] charsetStartKeywordsHTML4 = new String[]{"content=\"", "content=\'"};
		String[] charsetEndKeywordsHTML4 = new String[]{"\"", "\'"};
		String[] charsetStartKeywordsHTML4_2 = new String[]{"charset="};
		String[] charsetEndKeywordsHTML4_2 = new String[]{";", "\n"};

		try {

			data = new String(rawData, StandardCharsets.UTF_8);
		} catch (Exception e) {

			e.printStackTrace();
		}
		int cur = 0;
		while (true) {

			Object[] ret = parseDocWithToken(data, startKeywords, endKeywords, cur, false);
			if (2 != ret.length) {

				break;
			}
			cur = (int) ret[1];
			if (-1 == cur) {

				return "";
			}
			String metaData = (String) ret[0];
			Object[] ret2 = parseDocWithToken(metaData, charsetStartKeywords, charsetEndKeywords, 0, false);
			if (2 != ret2.length) {

				break;
			}
			int cur2 = (int) ret2[1];
			if (-1 != cur2) {

				return (String) ret2[0];
			}

			ret2 = parseDocWithToken(metaData, charsetStartKeywordsHTML4, charsetEndKeywordsHTML4, 0, false);
			if (2 != ret2.length) {

				break;
			}
			cur2 = (int) ret2[1];
			if (-1 == cur2) {

				continue;
			}
			String metaDataContentAttr = (String) ret2[0];
			ret2 = parseDocWithToken(metaDataContentAttr, charsetStartKeywordsHTML4_2, charsetEndKeywordsHTML4_2, 0,
					true);
			if (2 != ret2.length) {

				break;
			}
			cur2 = (int) ret2[1];
			if (-1 != cur2) {

				return (String) ret2[0];
			}
		}
		return "";
	}

	public String guessCharSetFromHttpHeader(byte[] rawData) {
		String startKeyword = "charset=";
		String[] endKeywords = new String[]{";", "\n"};
		HttpHeader header = new HttpHeader(rawData);
		Optional<String> enc = header.getValue("Content-type");
		int s, e = -1;

		if (!enc.isPresent()) {

			return "";
		}
		String data = enc.get().toLowerCase();
		s = data.indexOf(startKeyword);
		if (-1 == s) {

			return "";
		}
		s += startKeyword.length();
		for (String t : endKeywords) {

			e = data.indexOf(t, s);
			if (-1 != e) {

				break;
			}
		}
		if (-1 == e) {

			e = data.length();
		}
		return data.substring(s, e);
	}

	public boolean isAuto() {
		return isAuto;
	}

	public void setCharSet(String charSet) {
		setCharSet(charSet, false);
	}

	public void setCharSet(String charSet, boolean autoStatusUnchanged) {
		if (charSet == null) {

			return;
		}
		if (AUTO_CHARSET.equals(charSet)) {

			isAuto = true;
			return;
		}
		if (!autoStatusUnchanged) {

			isAuto = false;
		}
		if (isAuto) {

			for (String k : Charset.availableCharsets().keySet()) {

				String kLower = k.toLowerCase();
				if (kLower.equals(charSet.toLowerCase())) {

					this.charSet = k;
					return;
				}
			}
			log("%s is not supported charset", charSet);
		}
		if (getAvailableCharSetList().contains(charSet)) {

			this.charSet = charSet;
		} else {

			// TODO: Throw Exception
			log("%s is not supported charset", charSet);
		}
	}

	public String getCharSet() {
		return charSet;
	}

	public String getCharSetForGUIComponent() {
		if (isAuto) {

			return AUTO_CHARSET;
		}
		return charSet;
	}

	private String guessedCharSet(byte[] rawData) {
		String charset = guessCharSetFromHttpHeader(rawData);
		if (!"".equals(charset)) {

			return charset;
		}
		charset = guessCharSetFromMetatag(rawData);
		if (!"".equals(charset)) {

			return charset;
		}
		return DEFAULT_CHARSET;
	}

	public void setGuessedCharSet(byte[] rawData) {
		setCharSet(guessedCharSet(rawData), true);
	}

	public List<String> getAvailableCharSetList() {
		List<String> ret = new ArrayList<>();
		try {

			ret.add(AUTO_CHARSET);
			for (CharSet charset : CharSets.getInstance().queryAll()) {

				ret.add(charset.toString());
			}
		} catch (Exception e) {

			e.printStackTrace();
		}
		return ret;
	}

}
