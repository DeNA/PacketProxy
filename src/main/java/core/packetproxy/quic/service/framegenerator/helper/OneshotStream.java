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

package packetproxy.quic.service.framegenerator.helper;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;
import packetproxy.quic.value.frame.StreamFrame;

public class OneshotStream {
	private final Map<Long/* offset */, StreamFrame> frameMap = new HashMap<>();
	private final StreamId streamId;
	private boolean lastFrameReceived = false;
	private boolean alreadyResultReturned = false;
	private long totalLength = 0;

	public OneshotStream(StreamId streamId) {
		this.streamId = streamId;
	}

	public void put(StreamFrame frame) {
		if (frame.isFinished()) {
			this.lastFrameReceived = true;
			this.totalLength = frame.getOffset() + frame.getLength();
		}
		this.frameMap.put(frame.getOffset(), frame);
	}

	public Optional<QuicMessage> get() throws Exception {
		if (this.alreadyResultReturned) {
			return Optional.empty();
		}
		if (!this.lastFrameReceived) {
			return Optional.empty();
		}
		long offset = 0;
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		while (offset < this.totalLength) {
			StreamFrame frame = this.frameMap.get(offset);
			if (frame == null) {
				return Optional.empty();
			}
			offset += frame.getLength();
			data.write(frame.getStreamData());
		}
		this.alreadyResultReturned = true;
		return Optional.of(QuicMessage.of(this.streamId, data.toByteArray()));
	}
}
