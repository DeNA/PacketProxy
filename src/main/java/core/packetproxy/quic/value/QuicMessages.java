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

import lombok.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static packetproxy.util.Throwing.rethrow;

@ToString
@EqualsAndHashCode
public final class QuicMessages {

    static public QuicMessages emptyList() {
        return new QuicMessages();
    }

    static public QuicMessages of(QuicMessage msg) {
        return new QuicMessages(List.of(msg));
    }
    static public QuicMessages of(QuicMessage msg1, QuicMessage msg2) {
        return new QuicMessages(List.of(msg1, msg2));
    }
    static public QuicMessages of(QuicMessage msg1, QuicMessage msg2, QuicMessage msg3) {
        return new QuicMessages(List.of(msg1, msg2, msg3));
    }
    static public QuicMessages of(List<QuicMessage> msgs) {
        return new QuicMessages(msgs);
    }

    static public QuicMessages parse(byte[] bytes) {
        return parse(ByteBuffer.wrap(bytes));
    }

    static public QuicMessages parse(ByteBuffer buffer) {
        QuicMessages msgs = QuicMessages.emptyList();
        while (buffer.remaining() > 16) {
            int savedPosition = buffer.position();
            StreamId streamId = StreamId.parse(buffer);
            long dataLength = buffer.getLong();
            if (buffer.remaining() < dataLength) {
                buffer.position(savedPosition);
                break;
            }
            byte[] data = SimpleBytes.parse(buffer, dataLength).getBytes();
            msgs.add(QuicMessage.of(streamId, data));
        }
        return msgs;
    }

    private final List<QuicMessage> messages = new ArrayList<>();

    private QuicMessages() {
    }

    private QuicMessages(List<QuicMessage> msgs) {
        this.messages.addAll(msgs);
    }

    public void clear() {
        this.messages.clear();
    }

    public boolean add(QuicMessage msg) {
        return this.messages.add(msg);
    }

    public boolean addAll(QuicMessages msgs) {
        return this.messages.addAll(msgs.messages);
    }

    public QuicMessage get(int index) {
        return this.messages.get(index);
    }

    public int size() {
        return this.messages.size();
    }

    public void forEach(Consumer<QuicMessage> action) {
        this.messages.forEach(action);
    }

    public QuicMessages map(Function<QuicMessage, QuicMessage> mapper) {
        return QuicMessages.of(this.messages.stream().map(mapper).collect(Collectors.toList()));
    }

    public QuicMessages filter(Predicate<QuicMessage> predicate) {
        return QuicMessages.of(this.messages.stream().filter(predicate).collect(Collectors.toList()));
    }

    public byte[] getBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        this.messages.forEach(rethrow(msg -> {
            bytes.write(msg.getBytes());
        }));
        return bytes.toByteArray();
    }

    /**
     * streamIdのメッセージを集める
     * @param streamId
     * @return QuicMessages
     */
    public QuicMessages filter(StreamId streamId) {
        List<QuicMessage> msgs = this.messages.stream().filter(msg -> msg.streamIdIs(streamId)).collect(Collectors.toList());
        return new QuicMessages(msgs);
    }

    /**
     * streamId以外のメッセージを集める
     * @param streamId
     * @return QuicMessages
     */
    public QuicMessages filterAllBut(StreamId streamId) {
        List<QuicMessage> msgs = this.messages.stream().filter(msg -> !msg.streamIdIs(streamId)).collect(Collectors.toList());
        return new QuicMessages(msgs);
    }

}
