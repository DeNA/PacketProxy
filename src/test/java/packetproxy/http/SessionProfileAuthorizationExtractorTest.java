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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class SessionProfileAuthorizationExtractorTest {

	@Test
	public void extract_withAuthorizationHeader_returnsValue() {
		var request = ("GET /api/users HTTP/1.1\r\n" + "Host: example.com\r\n" + "Authorization: Bearer test-token\r\n"
				+ "Content-Length: 0\r\n" + "\r\n").getBytes();

		assertEquals("Bearer test-token", SessionProfileAuthorizationExtractor.extract(request));
	}

	@Test
	public void extract_withoutAuthorizationHeader_returnsEmptyString() {
		var request = ("GET /api/users HTTP/1.1\r\n" + "Host: example.com\r\n" + "Content-Length: 0\r\n" + "\r\n")
				.getBytes();

		assertEquals("", SessionProfileAuthorizationExtractor.extract(request));
	}

	@Test
	public void extract_withNonHttpData_returnsEmptyString() {
		assertEquals("", SessionProfileAuthorizationExtractor.extract(new byte[]{0x00, 0x01, 0x02}));
	}
}
