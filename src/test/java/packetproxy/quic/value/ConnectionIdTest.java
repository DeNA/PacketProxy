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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import packetproxy.quic.utils.Constants;

class ConnectionIdTest {

	@Test
	public void ランダムなConnectionIdを生成できること() {
		ConnectionId connId = ConnectionId.generateRandom();
		Assertions.assertEquals(Constants.CONNECTION_ID_SIZE, connId.getBytes().length);
	}

	@Test
	public void ByteArrayが同じならEqualになること() throws Exception {
		byte[] connIdBytes = Hex.decodeHex("11223344".toCharArray());
		ConnectionId connId1 = ConnectionId.of(connIdBytes);
		ConnectionId connId2 = ConnectionId.of(connIdBytes);
		assertEquals(connId1, connId2);
	}

	@Test
	public void 複数個のインスタンスがお互いにランダムになっていること() throws Exception {
		ConnectionId connId1 = ConnectionId.generateRandom();
		ConnectionId connId2 = ConnectionId.generateRandom();
		assertNotEquals(connId1, connId2);
	}

}
