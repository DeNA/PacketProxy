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

package packetproxy.quic.value.frame;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.Token;

class NewConnectionIdFrameTest {

	@Test
	public void smoke() throws Exception {
		NewConnectionIdFrame frame = new NewConnectionIdFrame(1, 0, ConnectionId.generateRandom(),
				Token.of(Hex.decodeHex("11223344556677889900112233445566".toCharArray())));
		byte[] data = frame.getBytes();

		NewConnectionIdFrame frame2 = NewConnectionIdFrame.parse(data);
		System.out.println(frame2);
		assertThat(frame).isEqualTo(frame2);
	}

}
