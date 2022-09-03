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

import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/*
QuicMessage {
  streamId: 8 bytes
  dataLength: 8 bytes
  data: ...
}
*/
@AllArgsConstructor(staticName = "of")
@Value
public class QuicMessage {

    static public List<QuicMessage> parse(ByteBuffer buffer) {
        List<QuicMessage> msgs = new ArrayList<>();
        while (buffer.remaining() > 16) {
            int savedPosition = buffer.position();
            long streamId = buffer.getLong();
            long dataLength = buffer.getLong();
            if (buffer.remaining() < dataLength) {
                buffer.position(savedPosition);
                break;
            }
            byte[] data = SimpleBytes.parse(buffer, dataLength).getBytes();
            msgs.add(new QuicMessage(streamId, data));
        }
        return msgs;
    }

    long streamId;
    byte[] data;

    /**
     * @return data to be passed to encoder module
     * streamId: 8 bytes
     * dataLength: 8 bytes
     * data: x bytes
     */
    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 16);
        buffer.putLong(this.streamId);
        buffer.putLong(this.data.length);
        buffer.put(data);
        return buffer.array();
    }

    @Override
    public String toString() {
        return String.format("QuicMessage(streamId=%d, data=[%s])",
                this.streamId,
                Hex.encodeHexString(this.data));
    }
}
