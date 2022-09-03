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

import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;

@Getter
public class VariableLengthPrecededBytes {

    static public VariableLengthPrecededBytes of(byte[] bytes) {
        return new VariableLengthPrecededBytes(bytes);
    }

    static public VariableLengthPrecededBytes parse(ByteBuffer buffer) {
        long length = VariableLengthInteger.parse(buffer).getValue();
        byte[] bytes = new byte[]{};
        if (length > 0) {
            bytes = new byte[(int) length];
            buffer.get(bytes);
        }
        return new VariableLengthPrecededBytes(bytes);
    }

    private byte[] bytes;

    private VariableLengthPrecededBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] serialize() {
        return ArrayUtils.addAll(VariableLengthInteger.of(bytes.length).getBytes(), bytes);
    }
}
