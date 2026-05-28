/*
 * Copyright 2026 DeNA Co., Ltd.
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

import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

public final class AppVersion {

	private static final String DEFAULT_VERSION = "1.0.0";
	private static String cached;

	private AppVersion() {
	}

	public static String get() {
		if (cached == null) {

			cached = load();
		}
		return cached;
	}

	private static String load() {
		try (InputStream in = AppVersion.class.getResourceAsStream("/version")) {
			if (in == null) {

				return DEFAULT_VERSION;
			}
			return IOUtils.toString(in).trim();
		} catch (IOException e) {

			return DEFAULT_VERSION;
		}
	}
}
