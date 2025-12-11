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

import org.junit.jupiter.api.Test;

class WriteStreamIdTest {

	@Test
	public void equalsが正常に動作すること() {
		StreamId id1 = StreamId.of(1);
		StreamId id2 = StreamId.of(1);
		StreamId id3 = StreamId.of(2);
		assertThat(id1).isEqualTo(id2);
		assertThat(id1).isNotEqualTo(id3);
		assertThat(id1.equals(id2)).isTrue();
	}
}
