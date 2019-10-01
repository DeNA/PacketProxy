/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.common;

// SIMDを利用する最適化がかかるように書けているはずなので、高速であって欲しい。
public class Hex {
    public byte[] encode(final byte[] raw) {
        if (raw == null) {
            throw new IllegalArgumentException();
        }
        final byte[] out = new byte[raw.length * 2];
        for (int i = 0; i < raw.length; i++) {
            int nbl1 = (raw[i] >> 4) & 0x0F;
            int nbl2 = raw[i] & 0x0F;
            nbl1 += 0x30;
            nbl2 += 0x30;
            nbl1 += (nbl1 - 0x30) / 10 * 39;
            nbl2 += (nbl2 - 0x30) / 10 * 39;
            out[2 * i] = (byte) nbl1;
            out[2 * i + 1] = (byte) nbl2;
        }
        return out;
    }

    public byte[] decode(final byte[] encoded) {
        if (encoded == null || (encoded.length & 0x01) > 0) {
            throw new IllegalArgumentException();
        }
        final byte[] out = new byte[encoded.length >> 1];
        for (int i = 0; i < encoded.length; i += 2) {
            int nbl1 = ((int) encoded[i]) & 0x00FF;
            int nbl2 = ((int) encoded[i + 1]) & 0x00FF;
            nbl1 -= (nbl1 / 0x60) * 32;
            nbl2 -= (nbl2 / 0x60) * 32;
            nbl1 -= (nbl1 / 0x40) * 7;
            nbl2 -= (nbl2 / 0x40) * 7;
            nbl1 -= 0x30;
            nbl2 -= 0x30;
            nbl1 <<= 4;
            out[i >> 1] = (byte) (nbl1 | nbl2);
        }
        return out;
    }

    public static boolean isHexString(String hexStr) {
        boolean result = true;
        for (byte b : hexStr.getBytes()) {
            int c = ((int) b) & 0x00FF;
            if (!(0x30 <= c && c <= 0x39) &&
                !(0x40 < c && c <= 0x46) &&
                !(0x60 < c && c <= 0x66)) {
                result = false;
                break;
            }
        }
        return result;
    }
}
