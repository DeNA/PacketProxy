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
package packetproxy.gui;

import static packetproxy.util.Logging.errWithStackTrace;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;

public class GUITooltipDecodeMessage {

	private byte[] raw_data;
	private String original;
	private String decoded;

	GUITooltipDecodeMessage(byte[] raw_text) {
		this.raw_data = raw_text;
		try {

			this.original = new String(raw_text, "UTF-8");
		} catch (UnsupportedEncodingException e) {

			errWithStackTrace(e);
		}
		this.decoded = null;
	}

	public String decodeMessage() {
		if (this.isURLEncodedText()) {

			this.decodeURLEncoding();
		} else if (this.isBase64Text()) {

			this.decodeBase64();
		} else {

			return original;
		}
		return decoded;
	}

	private void decodeURLEncoding() {
		try {

			decoded = URLDecoder.decode(original, "UTF-8");
		} catch (Exception e) {

			decoded = original;
		}
	}

	private void decodeBase64() {
		try {

			decoded = new String(Base64.decodeBase64(raw_data), "UTF-8");
		} catch (Exception e) {

			decoded = original;
		}
	}

	private boolean isURLEncodedText() {
		return original.substring(0, 1).equals("%");
	}

	private boolean isBase64Text() {
		return Pattern.matches("^[a-zA-Z0-9+/=]+$", original);
	}
}
