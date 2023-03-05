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

package packetproxy.http3.value.frame;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.VariableLengthInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RawFrameTest {

    @Test
    void RawFrameが正常に動作すること() throws Exception {
        RawFrame rawFrame = RawFrame.of(VariableLengthInteger.of(1).getBytes());
        assertThat(rawFrame.getBytes()).isEqualTo(Hex.decodeHex("01".toCharArray()));
    }

}