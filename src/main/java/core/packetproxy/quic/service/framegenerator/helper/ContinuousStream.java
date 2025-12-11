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
import lombok.Getter;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;
import packetproxy.quic.value.frame.StreamFrame;

@Getter
public class ContinuousStream {

	private final Map<Long /*offset*/, StreamFrame> frameMap = new HashMap<>();
	private final StreamId streamId;
	private long currentOffset = 0;
	private boolean finished = false;

	public ContinuousStream(StreamId streamId) {
		this.streamId = streamId;
	}

	public void put(StreamFrame frame) {
		if (frame.isFinished()) {

			this.finished = true;
		}
		this.frameMap.put(frame.getOffset(), frame);
	}

	public Optional<QuicMessage> get() throws Exception {
		ByteArrayOutputStream data = new ByteArrayOutputStream();

		StreamFrame frame = this.frameMap.get(this.currentOffset);
		if (frame == null) {

			return Optional.empty();
		}
		data.write(frame.getStreamData());
		this.currentOffset += frame.getLength();

		/* process continuous frame if they exist */
		while (this.frameMap.get(this.currentOffset) != null) {

			StreamFrame extraFrame = this.frameMap.get(this.currentOffset);
			data.write(extraFrame.getBytes());
			this.currentOffset += extraFrame.getLength();
		}

		return Optional.of(QuicMessage.of(this.streamId, data.toByteArray()));
	}
}
