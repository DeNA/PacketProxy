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

package packetproxy.http3.service;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.http3.value.Setting;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;

import static org.assertj.core.api.Assertions.assertThat;

class StreamsReaderTest {

    @Test
    void SettingsFrameを読み込めること() throws Exception {
        StreamsReader streams = new StreamsReader(Constants.Role.CLIENT);
        streams.write(QuicMessages.of(QuicMessage.of(StreamId.of(0x2), Hex.decodeHex("00040401000700".toCharArray()))));
        Setting settings = streams.getSetting().orElseThrow();
        assertThat(settings.getQpackMaxTableCapacity()).isEqualTo(0);
        assertThat(settings.getMaxFieldSectionSize()).isEqualTo(Long.MAX_VALUE);
    }

}