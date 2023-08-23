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

package packetproxy.http3.service.stream;

import packetproxy.http3.service.HttpRaw;
import packetproxy.http3.service.frame.FrameParser;
import packetproxy.http3.value.frame.*;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;

import java.io.ByteArrayOutputStream;

import static packetproxy.util.Throwing.rethrow;

public class HttpReadStream extends Stream implements ReadStream {

    private ByteArrayOutputStream headers = new ByteArrayOutputStream();
    private ByteArrayOutputStream data = new ByteArrayOutputStream();

    public HttpReadStream(StreamId streamId) {
        super(streamId, StreamType.NoStreamType);
    }

    public void write(Frame frame) throws Exception {
        if (frame instanceof HeadersFrame headersFrame) {
            this.headers.write(headersFrame.getData());
            return;
        }
        if (frame instanceof DataFrame dataFrame) {
            this.data.write(dataFrame.getData());
            return;
        }
        if (frame instanceof GreaseFrame) {
            System.out.println(frame);
            return;
        }
        throw new Exception("Error: write UnknownFrame(neither HeaderFrame nor DataFrame) to HttpStream.");
    }

    @Override
    public void write(QuicMessage msg) throws Exception {
        Frames frames = FrameParser.parse(msg.getData());
        frames.forEach(rethrow(this::write));
    }

    @Override
    public byte[] readAllBytes() {
        return new byte[]{}; /* not supported */
    }

    public byte[] readHeaderBytes() {
        return this.headers.toByteArray();
    }

    public byte[] readDataBytes() {
        return this.data.toByteArray();
    }

    public HttpRaw readHttpRaw() {
        return HttpRaw.of(streamId, this.readHeaderBytes(), this.readDataBytes());
    }

    public boolean isEmpty() {
        return this.headers.size() == 0;
    }

}
