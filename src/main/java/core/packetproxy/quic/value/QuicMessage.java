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

import lombok.Value;
import org.apache.commons.codec.binary.Hex;

import java.nio.ByteBuffer;

/*
QuicMessage {
  streamId: 8 bytes
  dataLength: 8 bytes
  data: ...
}
*/
@Value(staticConstructor = "of")
public class QuicMessage {

    StreamId streamId;
    byte[] data;

    boolean streamIdIs(StreamId streamId) {
        return streamId.equals(this.streamId);
    }

    /**
     * @return data to be passed to encoder module
     * streamId: 8 bytes
     * dataLength: 8 bytes
     * data: x bytes
     */
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 16);
        buffer.put(this.streamId.getBytes());
        buffer.putLong(this.data.length);
        buffer.put(data);
        return buffer.array();
    }

    @Override
    public String toString() {
        return String.format("QuicMessage(streamId=%s, dataLen=%d)", this.streamId, this.data.length);
    }
}
