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

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class I18nString {

	public static ResourceBundle bundle = null;
	public static Locale currentLocale = null;

	static {

		currentLocale = Locale.getDefault();
		if (currentLocale == Locale.JAPAN) {

			bundle = ResourceBundle.getBundle("strings");
		}
	}

	private static String normalize(String message) {
		return message.replace(' ', '_').replace('=', '_').replaceAll(":", "\\:").replaceAll(Pattern.quote("("), "\\(")
				.replaceAll(Pattern.quote(")"), "\\)");
	}

	public static Locale getLocale() {
		return currentLocale;
	}

	public static String get(String message, Object... args) {
		String localed = get(message);
		try {

			return String.format(localed, args);
		} catch (Exception e) {

			return String.format(message, args);
		}
	}

	public static String get(String message) {
		if (currentLocale == Locale.JAPAN) {

			try {

				String localeMsg = bundle.getString(normalize(message));
				return localeMsg.length() > 0 ? localeMsg : message;
			} catch (java.util.MissingResourceException e) {

				return message;
			} catch (Exception e) {

				System.err.println(String.format("[Error] can't read resource: %s", message));
				return message;
			}
		}
		return message;
	}

}
