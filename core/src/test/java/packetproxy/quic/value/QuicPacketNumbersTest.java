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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import packetproxy.quic.utils.PacketNumbers;

class QuicPacketNumbersTest {

	@Test
	void testLargest() throws Exception {
		PacketNumbers pns = new PacketNumbers();
		pns.add(PacketNumber.of(100L));
		pns.add(PacketNumber.of(1L));
		pns.add(PacketNumber.of(33L));
		pns.add(PacketNumber.of(0x7fffffffffffffffL));

		assertEquals(PacketNumber.of(0x7fffffffffffffffL), pns.largest());
	}
}
