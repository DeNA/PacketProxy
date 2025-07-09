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

package packetproxy.quic.value.packet.shortheader;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.PacketNumber;
import packetproxy.quic.value.key.Key;

class ShortHeaderPacketTest {

	@Test
	public void dataをインスタンス化した後getBytesで元に戻ること() throws Exception {
		byte[] data = Hex.decodeHex("5d7be5e8ca341134a2a0de5cced82ebb3f369cc7035c0b52465da032887b7a0c".toCharArray());
		byte[] secret = Hex.decodeHex("24ba37689b6e1e5ed9e1fbbf563718baeb2f11e4d1da18a04218761b386ab269".toCharArray());
		Key key = Key.of(secret);
		ShortHeaderPacket packet = new ShortHeaderPacket(ByteBuffer.wrap(data), key, PacketNumber.Infinite);
		byte[] restoredData = packet.getBytes(key, PacketNumber.Infinite);
		assertThat(data).isEqualTo(restoredData);
	}

}
