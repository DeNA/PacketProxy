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
import org.junit.jupiter.api.Test;

public class FrameTest {

	@Test
	public void extraTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		byte[] bbbbb = frame.getExtra();
		System.out.println(new String(bbbbb));
	}

	@Test
	public void removeExtraTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		frame.removeExtra();
		System.out.println(new String(Hex.encodeHex(frame.toByteArray())));
	}

	@Test
	public void saveExtraTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		frame.removeExtra();
		frame.saveExtra("0123456789".getBytes());
		System.out.println(new String(Hex.encodeHex(frame.toByteArray())));
	}

	@Test
	public void toByteArrayWithExtraDataTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		frame.removeExtra();
		frame.saveExtra("0123456789".getBytes());
		System.out.println(new String(Hex.encodeHex(frame.toByteArrayWithoutExtra())));
	}

}
