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

package packetproxy.http3.value.frame;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.http3.service.frame.FrameParser;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsFrameTest {

    @Test
    public void デフォルト値を追加したときgetBytesで値が消えること() throws Exception {
        /* Note: デフォルト値の場合は、敢えてデータを送信する必要はない */
        byte[] test = Hex.decodeHex("040401000700".toCharArray());
        SettingsFrame settingsFrame = SettingsFrame.parse(ByteBuffer.wrap(test));
        assertThat(settingsFrame.getBytes()).isEqualTo(Hex.decodeHex("0400".toCharArray()));
    }

    @Test
    public void Qpackフレームサイズに値を入れてパースしても元に戻ること() throws Exception {
        byte[] test = Hex.decodeHex("0402010a".toCharArray());
        SettingsFrame settingsFrame = SettingsFrame.parse(ByteBuffer.wrap(test));
        assertThat(settingsFrame.getBytes()).isEqualTo(Hex.decodeHex("0402010a".toCharArray()));
    }

    @Test
    public void デフォルト設定でSettingsFrameを生成できること() throws Exception {
        SettingsFrame settingsFrame = SettingsFrame.generateSettingsFrameWithDefaultValue();
        assertThat(settingsFrame.getBytes()).isEqualTo(Hex.decodeHex("0400".toCharArray()));
    }

    @Test
    public void curlのSettingsFrameをパースできること() throws Exception {
        byte[] test = Hex.decodeHex("040f06ffffffffffffffff010007003300".toCharArray());
        SettingsFrame settingsFrame = SettingsFrame.parse(ByteBuffer.wrap(test));
        assertThat(settingsFrame.getBytes()).isEqualTo(Hex.decodeHex("040906ffffffffffffffff".toCharArray()));
    }

    @Test
    public void www_google_comのSettingをパースできること2() throws Exception {
        byte[] test = Hex.decodeHex("041F018001000006800100000740640801C000001647FE8F66C000000068F2C54CC00000033AFE7BAE032CAD74".toCharArray());
        Frames frames = FrameParser.parse(ByteBuffer.wrap(test));
        assertThat(frames.size()).isEqualTo(2);
        assertThat(frames.get(0)).isInstanceOf(SettingsFrame.class);
        assertThat(frames.get(1)).isInstanceOf(GreaseFrame.class);
        System.out.println(frames);
    }

}