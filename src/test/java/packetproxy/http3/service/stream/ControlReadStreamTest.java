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

package packetproxy.http3.service.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;

class ControlReadStreamTest {

	@Test
	void 連続したFrameの順番が保たれること() throws Exception {
		ControlReadStream stream = new ControlReadStream(StreamId.of(0x2));
		stream.write(QuicMessage.of(StreamId.of(0x2), new byte[]{0x00, 0x04, 0x0})); // 0x40 0x00 は最小のSettingsFrame
		assertThat(stream.readAllBytes()).isEqualTo(Hex.decodeHex("0400".toCharArray()));
	}

}
