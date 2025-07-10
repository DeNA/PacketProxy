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

import java.nio.ByteBuffer;
import org.apache.commons.codec.binary.Hex;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.hpack.HpackDecoder;
import org.eclipse.jetty.http2.hpack.HpackEncoder;
import org.junit.jupiter.api.Test;
import packetproxy.http.Http;

public class HeadersFrameTest {

	private HpackEncoder encoder;
	private HpackDecoder decoder;

	public HeadersFrameTest() throws Exception {
		decoder = new HpackDecoder(4096, 4096);
		encoder = new HpackEncoder(4096, 4096);
	}

	@Test
	public void test() throws Exception {
		byte[] frame = Hex
				.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
		HeadersFrame fb = new HeadersFrame(frame, decoder);
		System.out.println(fb.toString());
	}

	@Test
	public void toHttp1Test() throws Exception {
		byte[] frame = Hex
				.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
		HeadersFrame fb = new HeadersFrame(frame, decoder);
		System.out.println(new String(fb.getHttp()));
	}

	@Test
	public void constructorHttpTest() throws Exception {
		byte[] frame = Hex
				.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
		HeadersFrame fb = new HeadersFrame(frame, decoder);
		byte[] b = fb.getHttp();
		Http http = Http.create(b);
		HeadersFrame fb2 = new HeadersFrame(http);
		System.out.println(fb);
		System.out.println(fb2);
	}

	@Test
	public void constructorHttpTest2() throws Exception {
		byte[] frame = Hex.decodeHex(
				"0001370104000000010085b8848d36a38264016e919d29ad171863c78f0bcc73cd415721e9635f92497ca589d34d1f6a1271d882a60e1bf0acf7008390692f96df697e940bea693f750400bea01cb816ae084a62d1bf6496df3dbf4a05f52f948a08017d4039702d5c1094c5a37f5891aed8e8313e94a47e561cc5804dbe20001f76036777735c821081408cf2b794216aec3a4a4498f57f0130408bf2b4b60e92ac7ad263d48f89dd0e8c1ab6e4c5934f40851d09591dc9ff07ed698907f371a699fe7ed4a47009b7c40003ed4ef07f2d39f4d33fcfd4ecadb00d820fe6e34d33fcfda948e0136f880007d4ecadb00d3f07f371a699fe7ed4a47009b7c40003ea7656d8069e83f9b8d34cff3f6a523804dbe20001f53b2b6c034e41fcdc69a67f9fb5291c026df10000fa9d95b601a660fe6e34d33fcfda948e0136f880007f"
						.toCharArray());
		HeadersFrame fb = new HeadersFrame(frame, decoder);
		byte[] b = fb.getHttp();
		Http http = Http.create(b);
		HeadersFrame fb2 = new HeadersFrame(http);
		System.out.println(frame.length);
		System.out.println(fb);
		System.out.println(fb2);
		System.out.println(new String(Hex.encodeHex(fb2.toByteArray())));
		System.out.println(new String(Hex.encodeHex(fb2.toByteArrayWithoutExtra(encoder))));
	}

	@Test
	public void bigData() throws Exception {
		byte[] a = Hex.decodeHex(
				"3fe15f0085b8848d36a38264026e959d29ad171863c78f0bcc73cd415721e963c1639ebf5885aec3771a4b5f92497ca589d34d1f6a1271d882a60e1bf0acf7788ca47e561cc58190b6cb80003f008390692f96df3dbf4a082a693f750400bea01cb8cb5704053168df76036777735c82109b408cf2b794216aec3a4a4498f57f0130408bf2b4b60e92ac7ad263d48f89dd0e8c1ab6e4c5934f00874152b10e7ea62fcb0eb8b2c3b601002fac10ac20ac073ed42f9acd615106e1a7e941056be522c2005f500e5c65ab820298b46ffb52b1a67818fb5243d2335502f31cf35055c87a7ed4dc3a4bb8c92c151ea2ff40851d09591dc9ff07ed698907f371a699fe7ed4a47009b7c40003ed4ef07f2d39f4d33fcfd4ecadb00d820fe6e34d33fcfda948e0136f880007d4ecadb00d3f07f371a699fe7ed4a47009b7c40003ea7656d8069e83f9b8d34cff3f6a523804dbe20001f53b2b6c034e41fcdc69a67f9fb5291c026df10000fa9d95b601a660fe6e34d33fcfda948e0136f880007f"
						.toCharArray());
		HpackDecoder decoder = new HpackDecoder(65535, 65535);
		ByteBuffer bb = ByteBuffer.allocate(4096);
		bb.put(a);
		bb.flip();
		MetaData m = decoder.decode(bb);
		System.out.println(m);
	}

	@Test
	public void extraDataTest() throws Exception {
		byte[] frameData = Hex.decodeHex(
				"0001370104000000010085b8848d36a38264016e919d29ad171863c78f0bcc73cd415721e9635f92497ca589d34d1f6a1271d882a60e1bf0acf7008390692f96df697e940bea693f750400bea01cb816ae084a62d1bf6496df3dbf4a05f52f948a08017d4039702d5c1094c5a37f5891aed8e8313e94a47e561cc5804dbe20001f76036777735c821081408cf2b794216aec3a4a4498f57f0130408bf2b4b60e92ac7ad263d48f89dd0e8c1ab6e4c5934f40851d09591dc9ff07ed698907f371a699fe7ed4a47009b7c40003ed4ef07f2d39f4d33fcfd4ecadb00d820fe6e34d33fcfda948e0136f880007d4ecadb00d3f07f371a699fe7ed4a47009b7c40003ea7656d8069e83f9b8d34cff3f6a523804dbe20001f53b2b6c034e41fcdc69a67f9fb5291c026df10000fa9d95b601a660fe6e34d33fcfda948e0136f880007f"
						.toCharArray());
		HpackDecoder decoder = new HpackDecoder(65535, 65535);
		HeadersFrame headerFrame = new HeadersFrame(frameData, decoder);
		System.out.println(new String(headerFrame.getExtra()));
	}

	@Test
	public void initHttpTest() throws Exception {
		byte[] frameData = Hex.decodeHex(
				"0001370104000000010085b8848d36a38264016e919d29ad171863c78f0bcc73cd415721e9635f92497ca589d34d1f6a1271d882a60e1bf0acf7008390692f96df697e940bea693f750400bea01cb816ae084a62d1bf6496df3dbf4a05f52f948a08017d4039702d5c1094c5a37f5891aed8e8313e94a47e561cc5804dbe20001f76036777735c821081408cf2b794216aec3a4a4498f57f0130408bf2b4b60e92ac7ad263d48f89dd0e8c1ab6e4c5934f40851d09591dc9ff07ed698907f371a699fe7ed4a47009b7c40003ed4ef07f2d39f4d33fcfd4ecadb00d820fe6e34d33fcfda948e0136f880007d4ecadb00d3f07f371a699fe7ed4a47009b7c40003ea7656d8069e83f9b8d34cff3f6a523804dbe20001f53b2b6c034e41fcdc69a67f9fb5291c026df10000fa9d95b601a660fe6e34d33fcfda948e0136f880007f"
						.toCharArray());
		HpackDecoder decoder = new HpackDecoder(65535, 65535);
		HeadersFrame headerFrame = new HeadersFrame(frameData, decoder);
		System.out.println(new String(headerFrame.getExtra()));
	}

}
