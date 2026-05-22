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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class BoyerMooreTest {

	@Test
	public void testEmptyPattern() {
		BoyerMoore bm = new BoyerMoore(new byte[]{});
		assertEquals(-1, bm.searchIn("Hello".getBytes()));
		assertEquals(-1, bm.searchIn("Hello".getBytes(), 1));
		assertEquals(-1, bm.searchIn("Hello".getBytes(), 1, 3));
	}

	@Test
	public void testEmptyPatternAndEmptyText() {
		BoyerMoore bm = new BoyerMoore(new byte[]{});
		assertEquals(-1, bm.searchIn("".getBytes()));
	}

	@Test
	public void testBasicCase() {
		String testString = "He says, 'hello world.'";
		BoyerMoore bm = new BoyerMoore("hello".getBytes());
		assertTrue(testString.substring(bm.searchIn(testString.getBytes())).startsWith("hello"));
	}

	@Test
	public void testFirstOccurrence() {
		String testString = "hell hello hello world";
		BoyerMoore bm = new BoyerMoore("hello".getBytes());
		assertTrue(testString.substring(bm.searchIn(testString.getBytes())).startsWith("hello h"));
	}

	@Test
	public void testOffset() {
		String testString = "'Hello world.' And he smiled and said Hello Work.";
		BoyerMoore bm = new BoyerMoore("Hello".getBytes());
		int idx = bm.searchIn(testString.getBytes(), 1);
		assertTrue(idx == 0);
		idx = bm.searchIn(testString.getBytes(), 2);
		assertTrue(idx == 36);
		assertTrue(testString.substring(38).startsWith("Hello Work"));
	}

	@Test
	public void testOutOfBoundsOffset() {
		String testString = "'Hello world.' And he smiled and said Hello Work.";
		BoyerMoore bm = new BoyerMoore("Hello".getBytes());
		try {

			bm.searchIn(testString.getBytes(), -1);
		} catch (ArrayIndexOutOfBoundsException e) {

			assertTrue(true);
			return;
		}
		try {

			bm.searchIn(testString.getBytes(), testString.length() - "Hello".getBytes().length);
		} catch (ArrayIndexOutOfBoundsException e) {

			assertTrue(true);
			return;
		}
		fail();
	}

	@Test
	public void testEndpos() {
		String testString = "'Hello world.' And he smiled and said Hello Work.";
		BoyerMoore bm = new BoyerMoore("Hello".getBytes());
		int idx = bm.searchIn(testString.getBytes(), 2);
		assertTrue(idx == 36);
		idx = bm.searchIn(testString.getBytes(), 2, 43);
		assertTrue(idx == 36);
		idx = bm.searchIn(testString.getBytes(), 2, 42);
		assertTrue(idx == -1);
	}

	@Test
	public void testMultiByte() {
		String testString = "こんにちは世界。さよなら人類。";
		BoyerMoore bm = new BoyerMoore("世界".getBytes());
		assertEquals("こんにちは".getBytes().length, bm.searchIn(testString.getBytes()));
	}

	@Test
	public void testFindHostHeader() {
		String header = "GET /v1/get_list?app_id=hoge&version=0.20.1 HTTP/1.1\r\n"
				+ "Authorization: Basic hogehogehoge\r\n" + "Host: www.example.com:4444\r\n" + "Accept: */*\r\n"
				+ "\r\n";
		String pattern = "Host:";
		BoyerMoore bm = new BoyerMoore(pattern.getBytes());
		assertEquals(header.indexOf(pattern), bm.searchIn(header.getBytes()));
	}
}
