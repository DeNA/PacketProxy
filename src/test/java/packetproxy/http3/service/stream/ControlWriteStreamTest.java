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

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.http3.value.Setting;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;

import static org.assertj.core.api.Assertions.assertThat;

class ControlWriteStreamTest {

    @Test
    void 最初のreadQuicMessagesでSreamIdが出力されること() throws Exception {
        ControlWriteStream stream = new ControlWriteStream(StreamId.of(0x2));
        stream.write(new byte[]{0x04, 0x00});
        QuicMessages msgs = stream.readAllQuicMessages();
        assertThat(msgs.get(0).getData()).isEqualTo(Hex.decodeHex("000400".toCharArray()));
        stream.write(new byte[]{0x04, 0x00});
        QuicMessages msgs2 = stream.readAllQuicMessages();
        assertThat(msgs2.get(0).getData()).isEqualTo(Hex.decodeHex("0400".toCharArray()));
    }

    @Test
    void Settingをwriteできること() throws Exception {
        ControlWriteStream stream = new ControlWriteStream(StreamId.of(0x2));
        Setting setting = Setting.builder().qpackMaxTableCapacity(100).build();
        stream.write(setting);
        QuicMessages msgs = stream.readAllQuicMessages();
        assertThat(msgs.get(0).getData()).isEqualTo(Hex.decodeHex("000403014064".toCharArray()));
    }
}