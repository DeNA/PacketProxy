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

import org.junit.jupiter.api.Test;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;

public class QpackWriteStreamTest {

	@Test
	void writeしたものがreadできること() throws Exception {
		QpackWriteStream stream = new QpackWriteStream(StreamId.of(0xa), Stream.StreamType.QpackDecoderStreamType);
		stream.write(new byte[]{0x11, 0x22, 0x33});
		QuicMessages msgs = stream.readAllQuicMessages();
		assertThat(msgs.size()).isEqualTo(1);
		assertThat(msgs.get(0)).isEqualTo(QuicMessage.of(StreamId.of(0xa), new byte[]{0x3, 0x11, 0x22, 0x33}));
	}

}
