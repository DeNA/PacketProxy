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

import org.apache.commons.lang3.ArrayUtils;
import packetproxy.http3.service.HttpRaw;
import packetproxy.http3.value.frame.DataFrame;
import packetproxy.http3.value.frame.HeadersFrame;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.QuicMessages;

import java.util.ArrayList;
import java.util.List;

public class HttpWriteStreams implements WriteStream {

    private final List<HttpRaw> httpRaws = new ArrayList<>();

    @Override
    public synchronized void write(byte[] data) throws Exception {
        /* not supported */
    }

    public synchronized void write(HttpRaw httpRaw) throws Exception {
        this.httpRaws.add(httpRaw);
    }

    @Override
    public synchronized QuicMessages readAllQuicMessages() {
        QuicMessages msgs = QuicMessages.emptyList();
        httpRaws.forEach(httpRaw -> {
            /* Header と Body を一緒にする*/
            byte[] headerBody = ArrayUtils.addAll(
                    HeadersFrame.of(httpRaw.getEncodedHeader()).getBytes(),
                    DataFrame.of(httpRaw.getBody()).getBytes());
            msgs.add(QuicMessage.of(httpRaw.getStreamId(), headerBody));
        });
        httpRaws.clear();
        return msgs;
    }

}
