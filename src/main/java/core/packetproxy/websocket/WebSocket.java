/*
 * Copyright 2019,2023 DeNA Co., Ltd.
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
package packetproxy.websocket;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.stream.Collectors;

import static packetproxy.util.Throwing.rethrowP;

public class WebSocket {

    LinkedList<WebSocketFrame> frames = new LinkedList<>();

    static public int checkDelimiter(byte[] data) {
        return WebSocketFrame.checkDelimiter(data);
    }

    public void frameArrived(byte[] data) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        WebSocketFrame frame = WebSocketFrame.parse(buffer);
        frames.add(frame);
        if (buffer.remaining() > 0) {
            throw new Exception("WebSocket: packet data is remaining.");
        }
    }

    public byte[] passThroughFrame() throws Exception {
        ByteArrayOutputStream passBytes = new ByteArrayOutputStream();
        this.frames = this.frames.stream().filter(rethrowP(frame -> {
            if (frame.getOpcode() != OpCode.Text && frame.getOpcode() != OpCode.Binary) {
                passBytes.write(frame.getBytes());
                //System.out.println("pass through: " + frame);
                return false;
            }
            return true;
        })).collect(Collectors.toCollection(LinkedList::new));
        return passBytes.toByteArray();
    }

    public byte[] frameAvailable() throws Exception {
        WebSocketFrame frame = this.frames.pollFirst();
        if (frame == null) {
            return null;
        }
        return frame.getPayload();
    }

}
