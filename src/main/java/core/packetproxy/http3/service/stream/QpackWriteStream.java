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
import static packetproxy.util.Logging.errWithStackTrace;

import java.io.ByteArrayOutputStream;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;
import packetproxy.quic.value.VariableLengthInteger;

public class QpackWriteStream extends Stream implements WriteStream {

	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	public QpackWriteStream(StreamId streamId, StreamType streamType) {
		super(streamId, streamType);
		try {

			buffer.write(VariableLengthInteger.of(super.streamType.type).getBytes());
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	public synchronized void write(byte[] data) throws Exception {
		buffer.write(data);
	}

	@Override
	public synchronized QuicMessages readAllQuicMessages() {
		QuicMessages msgs = QuicMessages.emptyList();
		if (buffer.size() > 0) {

			msgs.add(QuicMessage.of(super.streamId, buffer.toByteArray()));
			buffer.reset();
		}
		return msgs;
	}
}
