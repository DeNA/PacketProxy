/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.encode;

import java.nio.ByteBuffer;
import java.util.Arrays;
import packetproxy.model.Packet;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;

public class EncodeSampleQuic extends Encoder {

	public EncodeSampleQuic(String ALPN) {
		super(ALPN);
	}

	@Override
	public String getName() {
		return "Sample Quic";
	}

	/* 1つのリクエスト/レスポンスのサイズで区切ってください */
	@Override
	public int checkDelimiter(byte[] input_data) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(input_data);
		long streamId = buffer.getLong();
		long length = buffer.getLong();
		return (int) (8 + 8 + length);
	}

	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		return input_data;
	}

	@Override
	public byte[] encodeClientRequest(byte[] input_data) throws Exception {
		return input_data;
	}

	@Override
	public byte[] decodeServerResponse(byte[] input_data) throws Exception {
		return input_data;
	}

	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		return input_data;
	}

	private String getSummary(Packet packet) {
		QuicMessages messages = QuicMessages.parse(packet.getDecodedData());
		if (messages.size() > 0) {

			QuicMessage msg = messages.get(0);
			String direction = msg.getStreamId().isBidirectional() ? "[Bi]" : "[Uni]";
			String http3Info = "";
			if (Arrays.stream(new StreamId[]{StreamId.of(0x02), StreamId.of(0x03)})
					.anyMatch(id -> id.equals(msg.getStreamId()))) {

				http3Info = "HTTP3 Setting";
			} else if (Arrays.stream(new StreamId[]{StreamId.of(0x06), StreamId.of(0x07)})
					.anyMatch(id -> id.equals(msg.getStreamId()))) {

				http3Info = "HTTP3 QPACK Encoder";
			} else if (Arrays.stream(new StreamId[]{StreamId.of(0x0a), StreamId.of(0x0b)})
					.anyMatch(id -> id.equals(msg.getStreamId()))) {

				http3Info = "HTTP3 QPACK Decoder";
			}
			return String.format("%s %s %s", msg.getStreamId(), http3Info, direction);
		}
		return "Unknown QuicMessage";
	}

	@Override
	public String getSummarizedResponse(Packet packet) {
		return this.getSummary(packet);
	}

	@Override
	public String getSummarizedRequest(Packet packet) {
		return this.getSummary(packet);
	}

}
