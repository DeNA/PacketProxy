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

package packetproxy.quic.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuicMessagesTest {

	QuicMessages msgs;

	@BeforeEach
	void before() {
		this.msgs = QuicMessages.of(QuicMessage.of(StreamId.of(1), new byte[]{0x1}),
				QuicMessage.of(StreamId.of(2), new byte[]{0x2}), QuicMessage.of(StreamId.of(3), new byte[]{0x3}));
	}

	@Test
	void getが動作すること() {
		QuicMessage msg1 = QuicMessage.of(StreamId.of(1), new byte[]{0x1});
		QuicMessage msg2 = QuicMessage.of(StreamId.of(2), new byte[]{0x2});
		QuicMessage msg3 = QuicMessage.of(StreamId.of(3), new byte[]{0x3});
		assertThat(this.msgs.get(0)).isEqualTo(msg1);
		assertThat(this.msgs.get(1)).isEqualTo(msg2);
		assertThat(this.msgs.get(2)).isEqualTo(msg3);
	}

	@Test
	void equalsが動作すること() {
		QuicMessages msgs2 = QuicMessages.of(QuicMessage.of(StreamId.of(1), new byte[]{0x1}),
				QuicMessage.of(StreamId.of(2), new byte[]{0x2}), QuicMessage.of(StreamId.of(3), new byte[]{0x3}));
		assertThat(msgs2).isEqualTo(this.msgs);
	}

	@Test
	void forEachが動作すること() {
		this.msgs.forEach(msg -> {
			System.out.println(msg);
		});
	}

	@Test
	void filterが動作すること() {
		QuicMessages filteredMsg = this.msgs.filter(StreamId.of(0x2));
		QuicMessage expectedMsg = QuicMessage.of(StreamId.of(0x2), new byte[]{0x2});

		assertThat(filteredMsg.size()).isEqualTo(1);
		assertThat(filteredMsg.get(0)).isEqualTo(expectedMsg);
	}

	@Test
	void filterAllButが動作すること() {
		QuicMessages filteredMsg = this.msgs.filterAllBut(StreamId.of(0x2));
		QuicMessage expectedMsg1 = QuicMessage.of(StreamId.of(0x1), new byte[]{0x1});
		QuicMessage expectedMsg2 = QuicMessage.of(StreamId.of(0x3), new byte[]{0x3});

		assertThat(filteredMsg.size()).isEqualTo(2);
		assertThat(filteredMsg.get(0)).isEqualTo(expectedMsg1);
		assertThat(filteredMsg.get(1)).isEqualTo(expectedMsg2);
	}

	@Test
	void getBytesが動作すること() throws Exception {
		byte[] bytes = this.msgs.getBytes();
		assertThat(bytes).isEqualTo(Hex.decodeHex(
				"000000000000000100000000000000010100000000000000020000000000000001020000000000000003000000000000000103"
						.toCharArray()));
	}
}
