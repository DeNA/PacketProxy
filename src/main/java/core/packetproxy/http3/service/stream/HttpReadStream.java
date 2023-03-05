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
import packetproxy.http3.value.frame.DataFrame;
import packetproxy.http3.value.frame.Frame;
import packetproxy.http3.value.frame.Frames;
import packetproxy.http3.value.frame.HeadersFrame;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;

import java.util.Optional;

import static packetproxy.util.Throwing.rethrow;

public class HttpReadStream extends Stream implements ReadStream {

    private HeadersFrame headersFrame = null;
    private DataFrame dataFrame = null;

    public HttpReadStream(StreamId streamId) {
        super(streamId, StreamType.NoStreamType);
    }

    public void write(Frame frame) throws Exception {
        if (frame instanceof HeadersFrame) {
            this.headersFrame = (HeadersFrame)frame;
            return;
        }
        if (frame instanceof DataFrame) {
            this.dataFrame = (DataFrame)frame;
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
        return this.headersFrame != null ? this.headersFrame.getData() : new byte[]{};
    }

    public byte[] readDataBytes() {
        return this.dataFrame != null ? this.dataFrame.getData() : new byte[]{};
    }

    public HttpRaw readHttpRaw() {
        return HttpRaw.of(streamId, this.readHeaderBytes(), this.readDataBytes());
    }

    public boolean isEmpty() {
        return this.headersFrame == null;
    }

}
