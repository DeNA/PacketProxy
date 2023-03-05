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

package packetproxy.http3.service.frame;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.http3.value.frame.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static packetproxy.util.Throwing.rethrow;

class FrameParserTest {

    @Test
    public void SETTINGSフレームをパースできること() throws Exception {
        byte[] test = Hex.decodeHex("0400".toCharArray());
        Frames frames = FrameParser.parse(ByteBuffer.wrap(test));
        assertThat(frames.size()).isEqualTo(1);
        assertThat(frames.toList()).anyMatch(frame -> frame instanceof SettingsFrame);
    }

    @Test
    public void HEADERSフレームとDATAフレームをパースできること() throws Exception {
        byte[] test = Hex.decodeHex("0140410000db5f4d929c47604bb2b816bf838ffe9c95c292523acf5401395f1d92497ca58ae819aafb50938ec415305a99567b5f448e9d983f9b8d34cff3f6a52381c00300096e6f7420666f756e64".toCharArray());
        Frames frames = FrameParser.parse(ByteBuffer.wrap(test));
        assertThat(frames.toList()).anyMatch(frame -> frame instanceof HeadersFrame);
        assertThat(frames.toList()).anyMatch(frame -> frame instanceof DataFrame);
    }

    @Test
    public void SETTINGSフレームをパースして元に戻ること() throws Exception {
        byte[] test = Hex.decodeHex("0400".toCharArray());
        Frames frames = FrameParser.parse(ByteBuffer.wrap(test));
        assertThat(test).isEqualTo(frames.get(0).getBytes());
    }

    @Test
    public void HEADERSフレームとDATAフレームをパースして元に戻ること() throws Exception {
        byte[] test = Hex.decodeHex("0140410000db5f4d929c47604bb2b816bf838ffe9c95c292523acf5401395f1d92497ca58ae819aafb50938ec415305a99567b5f448e9d983f9b8d34cff3f6a52381c00300096e6f7420666f756e64".toCharArray());
        ByteArrayOutputStream framesBuffer = new ByteArrayOutputStream();
        FrameParser.parse(ByteBuffer.wrap(test)).forEach(rethrow(frame -> {
            framesBuffer.write(frame.getBytes());
        }));
        assertThat(test).isEqualTo(framesBuffer.toByteArray());
    }

}