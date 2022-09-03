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

package packetproxy.quic.value.frame;

import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

import java.nio.ByteBuffer;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class CryptoFrame extends Frame {

    static public final byte TYPE = 0x06;

    static public List<Byte> supportedTypes() {
        return ImmutableList.of(TYPE);
    }

    long offset;

    byte[] data;

    static public CryptoFrame parse(byte[] bytes) {
        return CryptoFrame.parse(ByteBuffer.wrap(bytes));
    }

    static public CryptoFrame parse(ByteBuffer buffer) {
        byte type = buffer.get();
        assert(type == TYPE);
        long offset = VariableLengthInteger.parse(buffer).getValue();
        long length = VariableLengthInteger.parse(buffer).getValue();
        byte[] data = SimpleBytes.parse(buffer, length).getBytes();
        return new CryptoFrame(offset, data);
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(1500);
        buffer.put(TYPE);
        buffer.put(VariableLengthInteger.of(this.offset).getBytes());
        buffer.put(VariableLengthInteger.of(this.data.length).getBytes());
        buffer.put(data);
        buffer.flip();
        return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
    }

    @Override
    public String toString() {
        return String.format("CryptFrame(offset=%d, length=%d, data=%s)",
                this.offset,
                this.data.length,
                Hex.encodeHexString(this.data));
    }

    @Override
    public boolean isAckEliciting() {
        return true;
    }
}
