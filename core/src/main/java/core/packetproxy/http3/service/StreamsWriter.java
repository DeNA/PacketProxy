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

import packetproxy.http3.service.stream.ControlWriteStream;
import packetproxy.http3.service.stream.HttpWriteStreams;
import packetproxy.http3.service.stream.QpackWriteStream;
import packetproxy.http3.service.stream.Stream;
import packetproxy.http3.value.Setting;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;

public class StreamsWriter {

	private final HttpWriteStreams httpWriteStreams = new HttpWriteStreams();
	private final ControlWriteStream controlWriteStream;
	private final QpackWriteStream qpackEncodeStreamWriter;
	private final QpackWriteStream qpackDecodeStreamWriter;

	public StreamsWriter(Constants.Role role) {
		if (role == Constants.Role.CLIENT) {

			this.controlWriteStream = new ControlWriteStream(StreamId.of(0x3));
			this.qpackEncodeStreamWriter = new QpackWriteStream(StreamId.of(0x7),
					Stream.StreamType.QpackEncoderStreamType);
			this.qpackDecodeStreamWriter = new QpackWriteStream(StreamId.of(0xb),
					Stream.StreamType.QpackDecoderStreamType);
		} else {

			this.controlWriteStream = new ControlWriteStream(StreamId.of(0x2));
			this.qpackEncodeStreamWriter = new QpackWriteStream(StreamId.of(0x6),
					Stream.StreamType.QpackEncoderStreamType);
			this.qpackDecodeStreamWriter = new QpackWriteStream(StreamId.of(0xa),
					Stream.StreamType.QpackDecoderStreamType);
		}
	}

	public synchronized void writeSetting(Setting setting) {
		this.controlWriteStream.write(setting);
	}

	public synchronized void writeQpackEncodeData(byte[] data) throws Exception {
		this.qpackEncodeStreamWriter.write(data);
	}

	public synchronized void writeQpackDecodeData(byte[] data) throws Exception {
		this.qpackDecodeStreamWriter.write(data);
	}

	public synchronized void write(HttpRaw httpRaw) throws Exception {
		this.httpWriteStreams.write(httpRaw);
	}

	public synchronized QuicMessages readQuickMessages() throws Exception {
		QuicMessages msgs = QuicMessages.emptyList();
		msgs.addAll(this.controlWriteStream.readAllQuicMessages());
		msgs.addAll(this.qpackEncodeStreamWriter.readAllQuicMessages());
		msgs.addAll(this.qpackDecodeStreamWriter.readAllQuicMessages());
		msgs.addAll(this.httpWriteStreams.readAllQuicMessages());
		return msgs;
	}
}
