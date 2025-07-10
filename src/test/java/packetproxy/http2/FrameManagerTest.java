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
package packetproxy.http2;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;

public class FrameManagerTest {

	private byte[] requestFrames;
	private byte[] responseFrames;
	private byte[] settingsFrame;

	public FrameManagerTest() throws Exception {
		requestFrames = Hex.decodeHex(
				"0000040800000000003FFF000100001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A000000040100000000"
						.toCharArray());
		responseFrames = Hex.decodeHex(
				"000004080000000000000F00010000000401000000000001370104000000010085B8848D36A38264016E919D29AD171863C78F0BCC73CD415721E9635F92497CA589D34D1F6A1271D882A60E1BF0ACF7008390692F96DF697E940BEA693F750400BEA01CB816AE084A62D1BF6496DF3DBF4A05F52F948A08017D4039702D5C1094C5A37F5891AED8E8313E94A47E561CC5804DBE20001F76036777735C821081408CF2B794216AEC3A4A4498F57F0130408BF2B4B60E92AC7AD263D48F89DD0E8C1AB6E4C5934F40851D09591DC9FF07ED698907F371A699FE7ED4A47009B7C40003ED4EF07F2D39F4D33FCFD4ECADB00D820FE6E34D33FCFDA948E0136F880007D4ECADB00D3F07F371A699FE7ED4A47009B7C40003EA7656D8069E83F9B8D34CFF3F6A523804DBE20001F53B2B6C034E41FCDC69A67F9FB5291C026DF10000FA9D95B601A660FE6E34D33FCFDA948E0136F880007F0000DC0000000000013C48544D4C3E3C484541443E3C6D65746120687474702D65717569763D22636F6E74656E742D747970652220636F6E74656E743D22746578742F68746D6C3B636861727365743D7574662D38223E0A3C5449544C453E333031204D6F7665643C2F5449544C453E3C2F484541443E3C424F44593E0A3C48313E333031204D6F7665643C2F48313E0A54686520646F63756D656E7420686173206D6F7665640A3C4120485245463D2268747470733A2F2F7777772E676F6F676C652E636F6D2F223E686572653C2F413E2E0D0A3C2F424F44593E3C2F48544D4C3E0D0A000000000100000001"
						.toCharArray());
		settingsFrame = Hex.decodeHex("000012040000000000000300000064000400100000000600004000".toCharArray());
	}

	@Test
	public void parseRequestFramesTest() throws Exception {
		FrameManager h2 = new FrameManager();
		h2.write(settingsFrame);
		for (Frame frame : FrameUtils.parseFrames(requestFrames)) {

			System.out.println(frame.toString());
		}
	}

	@Test
	public void parseResponseFramesTest() throws Exception {
		FrameManager h2 = new FrameManager();
		h2.write(settingsFrame);
		for (Frame frame : FrameUtils.parseFrames(responseFrames)) {

			System.out.println(frame.toString());
		}
	}

	@Test
	public void requestTest() throws Exception {
		FrameManager h2 = new FrameManager();
		h2.write(settingsFrame);
		h2.write(requestFrames);
		h2.readHeadersDataFrames();
	}

	@Test
	public void responseTest() throws Exception {
		FrameManager h2 = new FrameManager();
		h2.write(settingsFrame);
		h2.write(responseFrames);
		h2.readControlFrames();
	}

}
