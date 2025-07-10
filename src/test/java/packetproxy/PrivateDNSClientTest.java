/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class PrivateDNSClientTest {

	@Test
	public void 成功するケース() throws Exception {
		ArrayList<String> lines = new ArrayList<>();
		lines.add("127.0.0.1 aaa aaa.example.com # for test");
		assertTrue(PrivateDNSClient.dnsLoopingFromHostsLines(lines, "aaa.example.com"));
	}

	@Test
	public void 成功するケース2() throws Exception {
		ArrayList<String> lines = new ArrayList<>();
		lines.add("2.3.4.5 aaa bbb.example.com");
		lines.add("127.0.0.1 aaa aaa.example.com");
		assertTrue(PrivateDNSClient.dnsLoopingFromHostsLines(lines, "aaa.example.com"));
	}

	@Test
	public void 成功するケース3() throws Exception {
		ArrayList<String> lines = new ArrayList<>();
		lines.add(" # this is a comment.");
		lines.add("# 2.3.4.5 aaa bbb.example.com");
		lines.add("127.0.0.1 aaa aaa.example.com");
		lines.add(" # 3.3.3.3 aaa ccc.example.com");
		assertTrue(PrivateDNSClient.dnsLoopingFromHostsLines(lines, "aaa.example.com"));
	}

	@Test
	public void 失敗するケース() throws Exception {
		ArrayList<String> lines = new ArrayList<>();
		lines.add("127.0.0.2 aaa aaa.example.com # for test");
		assertFalse(PrivateDNSClient.dnsLoopingFromHostsLines(lines, "aaa.example.com"));
	}

	@Test
	public void 失敗するケース2() throws Exception {
		ArrayList<String> lines = new ArrayList<>();
		lines.add("# 127.0.0.2 aaa aaa.example.com");
		assertFalse(PrivateDNSClient.dnsLoopingFromHostsLines(lines, "aaa.example.com"));
	}

	@Test
	public void 失敗するケース3() throws Exception {
		ArrayList<String> lines = new ArrayList<>();
		lines.add("# 127.0.0.2 aaa bbb.example.com");
		assertFalse(PrivateDNSClient.dnsLoopingFromHostsLines(lines, "aaa.example.com"));
	}

	@Test
	public void 失敗するケース4() throws Exception {
		ArrayList<String> lines = new ArrayList<>();
		lines.add(" # this is a comment.");
		lines.add("# 2.3.4.5 aaa bbb.example.com");
		lines.add("127.0.0.1 aaa ddd.example.com");
		lines.add(" # 3.3.3.3 aaa ccc.example.com");
		assertFalse(PrivateDNSClient.dnsLoopingFromHostsLines(lines, "aaa.example.com"));
	}

}
