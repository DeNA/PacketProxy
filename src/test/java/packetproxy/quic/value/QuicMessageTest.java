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

package packetproxy.quic.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

class QuicMessageTest {

	@Test
	public void 一つのQuicMessageをparseできること() throws Exception {
		byte[] testData = Hex.decodeHex("00000000000000030000000000000001ab".toCharArray());
		QuicMessages msgs = QuicMessages.parse(testData);
		assertThat(msgs.get(0).getData()).isEqualTo(Hex.decodeHex("ab".toCharArray()));
	}

	@Test
	public void 複数のQuicMessageをparseできること() throws Exception {
		byte[] data = Hex
				.decodeHex("00000000000000030000000000000001ab000000000000000100000000000000021234".toCharArray());
		QuicMessages msgs = QuicMessages.parse(data);
		assertThat(msgs.size()).isEqualTo(2);
		assertThat(msgs.get(0).getStreamId()).isEqualTo(StreamId.of(0x3));
		assertThat(msgs.get(0).getData()).isEqualTo(Hex.decodeHex("ab".toCharArray()));
		assertThat(msgs.get(1).getStreamId()).isEqualTo(StreamId.of(0x1));
		assertThat(msgs.get(1).getData()).isEqualTo(Hex.decodeHex("1234".toCharArray()));
	}

	@Test
	public void streamIdIsが動作すること() throws Exception {
		byte[] data = Hex
				.decodeHex("00000000000000030000000000000001ab000000000000000100000000000000021234".toCharArray());
		QuicMessages msgs = QuicMessages.parse(data);
		QuicMessage msg = msgs.get(0);
		assertThat(msg.streamIdIs(StreamId.of(0x3))).isTrue();
		assertThat(msg.streamIdIs(StreamId.of(0x4))).isFalse();
	}

}
