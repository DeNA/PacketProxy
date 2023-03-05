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

import com.google.common.collect.ImmutableList;
import lombok.Value;
import packetproxy.quic.value.VariableLengthInteger;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Value
public class DummyFrame implements Frame {

    static public final long TYPE = 0x33; /* dummy type */

    static public DummyFrame of(long value) {
        return new DummyFrame(value);
    }

    static public List<Long> supportedTypes() {
        return ImmutableList.of(TYPE);
    }

    long type;
    long value;

    private DummyFrame(long value) {
        this.type = TYPE;
        this.value = value;
    }

    public byte[] getData() {
        return VariableLengthInteger.of(this.value).getBytes();
    }

    @Override
    public byte[] getBytes() throws Exception {
        ByteArrayOutputStream dataFrameStream = new ByteArrayOutputStream();
        dataFrameStream.write(VariableLengthInteger.of(this.type).getBytes());
        dataFrameStream.write(VariableLengthInteger.of(this.value).getBytes());
        return dataFrameStream.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("DummyFrame(value=%d)", this.value);
    }
}
