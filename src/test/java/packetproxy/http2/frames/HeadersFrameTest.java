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
package packetproxy.http2.frames;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import packetproxy.http.Http;

public class HeadersFrameTest {

	@Test
	public void test() throws Exception {
		byte[] frame = Hex.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
		HeadersFrame fb = new HeadersFrame(frame);
		System.out.println(fb.toString());
	}

	@Test
	public void toHttp1Test() throws Exception {
		byte[] frame = Hex.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
		HeadersFrame fb = new HeadersFrame(frame);
		System.out.println(new String(fb.toHttp1()));
	}

	@Test
	public void constractorHttpTest() throws Exception {
		byte[] frame = Hex.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
		HeadersFrame fb = new HeadersFrame(frame);
		byte[] b = fb.toHttp1();
		Http http = new Http(b);
		HeadersFrame fb2 = new HeadersFrame(http);
		System.out.println(fb);
		System.out.println(fb2);
	}

	@Test
	public void constractorHttpTest2() throws Exception {
		byte[] frame = Hex.decodeHex("0001370104000000010085b8848d36a38264016e919d29ad171863c78f0bcc73cd415721e9635f92497ca589d34d1f6a1271d882a60e1bf0acf7008390692f96df697e940bea693f750400bea01cb816ae084a62d1bf6496df3dbf4a05f52f948a08017d4039702d5c1094c5a37f5891aed8e8313e94a47e561cc5804dbe20001f76036777735c821081408cf2b794216aec3a4a4498f57f0130408bf2b4b60e92ac7ad263d48f89dd0e8c1ab6e4c5934f40851d09591dc9ff07ed698907f371a699fe7ed4a47009b7c40003ed4ef07f2d39f4d33fcfd4ecadb00d820fe6e34d33fcfda948e0136f880007d4ecadb00d3f07f371a699fe7ed4a47009b7c40003ea7656d8069e83f9b8d34cff3f6a523804dbe20001f53b2b6c034e41fcdc69a67f9fb5291c026df10000fa9d95b601a660fe6e34d33fcfda948e0136f880007f".toCharArray());
		HeadersFrame fb = new HeadersFrame(frame);
		byte[] b = fb.toHttp1();
		Http http = new Http(b);
		HeadersFrame fb2 = new HeadersFrame(http);
		System.out.println(fb);
		System.out.println(fb2);
	}

}
