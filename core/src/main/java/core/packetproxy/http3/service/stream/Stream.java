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

import lombok.AllArgsConstructor;
import lombok.Getter;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;

public abstract class Stream {

	@Getter
	@AllArgsConstructor
	public enum StreamType {
		ControlStreamType(0x0), QpackEncoderStreamType(0x2), QpackDecoderStreamType(0x3), NoStreamType(0x4);
		final long type;

		public static StreamType of(final int typeId) {
			for (StreamType streamType : StreamType.values()) {

				if (streamType.type == typeId) {

					return streamType;
				}
			}
			return null;
		}
	}

	public StreamId streamId;
	public StreamType streamType;

	public Stream(StreamId streamId, StreamType streamType) {
		this.streamId = streamId;
		this.streamType = streamType;
	}

	public boolean processable(StreamId streamId) {
		return this.streamId.equals(streamId);
	}

	public boolean processable(QuicMessage msg) {
		return this.processable(msg.getStreamId());
	}

	public boolean streamTypeEquals(long type) {
		return this.streamType.type == type;
	}
}
