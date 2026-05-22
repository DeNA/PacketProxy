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

package packetproxy.quic.service.framegenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import packetproxy.quic.service.framegenerator.helper.ContinuousStream;
import packetproxy.quic.service.framegenerator.helper.OneshotStream;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;
import packetproxy.quic.value.frame.StreamFrame;

public class StreamFramesToMessages {

	private final Map<StreamId, ContinuousStream> continuousStreamMap = new HashMap<>();
	private final Map<StreamId, OneshotStream> oneshotStreamMap = new HashMap<>();

	public void put(StreamFrame frame) {
		StreamId streamId = frame.getStreamId();
		if (streamId.isBidirectional()) {

			this.putToOneshot(frame);
		} else {
			/* uni-directional */

			this.putToContinuous(frame);
		}
	}

	private void putToContinuous(StreamFrame frame) {
		StreamId streamId = frame.getStreamId();
		if (!continuousStreamMap.containsKey(streamId)) {

			continuousStreamMap.put(streamId, new ContinuousStream(streamId));
		}
		this.continuousStreamMap.get(streamId).put(frame);
	}

	private void putToOneshot(StreamFrame frame) {
		StreamId streamId = frame.getStreamId();
		if (!oneshotStreamMap.containsKey(streamId)) {

			oneshotStreamMap.put(streamId, new OneshotStream(streamId));
		}
		this.oneshotStreamMap.get(streamId).put(frame);
	}

	public Optional<QuicMessage> get(StreamId streamId) {
		if (streamId.isBidirectional()) {

			return this.getFromOneshot(streamId);
		} else {
			/* uni-directional */

			return this.getFromContinuous(streamId);
		}
	}

	@SneakyThrows
	private Optional<QuicMessage> getFromContinuous(StreamId streamId) {
		if (!this.continuousStreamMap.containsKey(streamId)) {

			return Optional.empty();
		}
		return this.continuousStreamMap.get(streamId).get();
	}

	@SneakyThrows
	private Optional<QuicMessage> getFromOneshot(StreamId streamId) {
		if (!this.oneshotStreamMap.containsKey(streamId)) {

			return Optional.empty();
		}
		return this.oneshotStreamMap.get(streamId).get();
	}
}
