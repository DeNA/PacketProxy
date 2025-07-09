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

import static org.assertj.core.api.Assertions.*;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;

class QpackReadStreamTest {

	@Test
	void writeしたものがreadできること() throws Exception {
		QpackReadStream stream = new QpackReadStream(StreamId.of(0xa), Stream.StreamType.QpackEncoderStreamType);
		stream.write(QuicMessage.of(StreamId.of(0xa), Hex.decodeHex("02112233".toCharArray())));
		byte[] bytes = stream.readAllBytes();
		assertThat(bytes.length).isEqualTo(3);
		assertThat(bytes).isEqualTo(Hex.decodeHex("112233".toCharArray()));
	}

	@Test
	void streamTypeが異なるものがwriteされたら例外が起きること() throws Exception {
		QpackReadStream stream = new QpackReadStream(StreamId.of(0xa), Stream.StreamType.QpackEncoderStreamType);
		assertThatThrownBy(() -> {
			stream.write(QuicMessage.of(StreamId.of(0xa), Hex.decodeHex("03112233".toCharArray())));
		}).isInstanceOf(Exception.class);
	}

}
