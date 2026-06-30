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
package packetproxy.http;

public class SessionProfileAuthorizationExtractor {

	private SessionProfileAuthorizationExtractor() {
	}

	public static String extract(byte[] requestData) {
		if (requestData == null || requestData.length == 0) {
			return "";
		}
		if (!HttpHeader.isHTTPHeader(requestData)) {
			return "";
		}
		try {
			return Http.create(requestData).getFirstHeader("Authorization");
		} catch (Exception e) {
			return "";
		}
	}
}
