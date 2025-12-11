/*
 * Copyright 2023 DeNA Co., Ltd.
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

package packetproxy.http3.value.frame;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

class DummyFrameTest {

	@Test
	void 値1を保存できること() throws Exception {
		DummyFrame dummy = DummyFrame.of(0x1);
		assertThat(dummy.getBytes()).isEqualTo(Hex.decodeHex("3301".toCharArray()));
	}

	@Test
	void 値2を保存できること() throws Exception {
		DummyFrame dummy = DummyFrame.of(0x2);
		assertThat(dummy.getBytes()).isEqualTo(Hex.decodeHex("3302".toCharArray()));
	}
}
