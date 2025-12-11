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

public class SettingsFrameTest {

	@Test
	public void smoke() throws Exception {
		byte[] data = Hex.decodeHex("000012040000000000000300000064000400100000000600004000".toCharArray());
		SettingsFrame sf = new SettingsFrame(data);
		System.out.println(sf);
	}
}
