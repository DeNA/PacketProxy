/*
 * Copyright 2022 DeNA Co., Ltd.

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

package packetproxy.http3.service;

import packetproxy.http3.service.stream.ControlReadStream;
import packetproxy.http3.service.stream.HttpReadStream;
import packetproxy.http3.service.stream.QpackReadStream;
import packetproxy.http3.service.stream.Stream;
import packetproxy.http3.value.Setting;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static packetproxy.util.Throwing.rethrow;

public class StreamsReader {

    private final Map<StreamId, HttpReadStream> httpStreams = new HashMap<>();
    private final ControlReadStream controlReadStream;
    private final QpackReadStream qpackEncodeStreamReader;
    private final QpackReadStream qpackDecodeStreamReader;

    public StreamsReader(Constants.Role role) {
        if (role == Constants.Role.CLIENT) {
            this.controlReadStream = new ControlReadStream(StreamId.of(0x2));
            this.qpackEncodeStreamReader = new QpackReadStream(StreamId.of(0x6), Stream.StreamType.QpackEncoderStreamType);
            this.qpackDecodeStreamReader = new QpackReadStream(StreamId.of(0xa), Stream.StreamType.QpackDecoderStreamType);
        } else {
            this.controlReadStream = new ControlReadStream(StreamId.of(0x3));
            this.qpackEncodeStreamReader = new QpackReadStream(StreamId.of(0x7), Stream.StreamType.QpackEncoderStreamType);
            this.qpackDecodeStreamReader = new QpackReadStream(StreamId.of(0xb), Stream.StreamType.QpackDecoderStreamType);
        }
    }

    public synchronized void write(QuicMessage msg) throws Exception {
        if (this.controlReadStream.processable(msg)) {
            this.controlReadStream.write(msg);
        } else if (this.qpackDecodeStreamReader.processable(msg)) {
            this.qpackDecodeStreamReader.write(msg);
        } else if (this.qpackEncodeStreamReader.processable(msg)) {
            this.qpackEncodeStreamReader.write(msg);
        } else {
            if (!this.httpStreams.containsKey(msg.getStreamId())) {
                this.httpStreams.put(msg.getStreamId(), new HttpReadStream(msg.getStreamId()));
            }
            this.httpStreams.get(msg.getStreamId()).write(msg);
        }
    }

    public synchronized void write(QuicMessages msgs) {
        msgs.forEach(rethrow(this::write));
    }

    public synchronized Optional<Setting> getSetting() {
        return this.controlReadStream.getSetting();
    }

    public synchronized byte[] readQpackEncodeData() {
        return this.qpackEncodeStreamReader.readAllBytes();
    }

    public synchronized byte[] readQpackDecodeData() {
        return this.qpackDecodeStreamReader.readAllBytes();
    }

    public Optional<HttpRaw> readHttpRaw() {
        AtomicReference<Optional<HttpRaw>> httpRaw = new AtomicReference<>(Optional.empty());
        this.httpStreams.entrySet().stream()
                .filter(ent -> ent.getKey().isBidirectional())
                .filter(ent -> !ent.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst().ifPresent(rethrow(streamId -> {
                    HttpReadStream httpStreamReader = this.httpStreams.get(streamId);
                    httpRaw.set(Optional.of(httpStreamReader.readHttpRaw()));
                    httpStreams.remove(streamId);
                }));
        return httpRaw.get();
    }

}
