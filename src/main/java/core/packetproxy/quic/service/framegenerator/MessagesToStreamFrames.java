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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;
import packetproxy.quic.value.frame.Frame;
import packetproxy.quic.value.frame.StreamFrame;

public class MessagesToStreamFrames {

	private final List<Frame> frameList = new ArrayList<>();
	private final Map<StreamId, Long /* current Offset */> continuousStreamMap = new HashMap<>();

	public synchronized void put(QuicMessage msg) {
		StreamId streamId = msg.getStreamId();

		if (streamId.isBidirectional()) { /* bi-directional stream */

			byte[] data = msg.getData();
			int remaining = data.length;
			int subOffset = 0;
			while (remaining > 0) {

				int subLength = Math.min(remaining, 1200);
				byte[] subData = ArrayUtils.subarray(data, subOffset, subOffset + subLength);
				boolean finishFlag = (subOffset + subLength == data.length);
				this.frameList.add(StreamFrame.of(streamId, subOffset, subLength, subData, finishFlag));
				remaining -= subLength;
				subOffset += subLength;
			}

		} else { /* uni-directional stream */

			Long offsetObj = this.continuousStreamMap.get(streamId);
			long offset = 0;
			if (offsetObj != null) {

				offset = offsetObj;
			}
			byte[] data = msg.getData();
			int remaining = data.length;
			int subOffset = 0;
			while (remaining > 0) {

				int subLength = Math.min(remaining, 1200);
				byte[] subData = ArrayUtils.subarray(data, subOffset, subOffset + subLength);
				this.frameList.add(StreamFrame.of(streamId, offset, subLength, subData, false));
				remaining -= subLength;
				subOffset += subLength;
				offset += subLength;
			}
			this.continuousStreamMap.put(streamId, offset);
		}
	}

	/**
	 * get and remove all StreamFrames
	 */
	public synchronized Frames get() {
		Frames frames = Frames.of(this.frameList);
		this.frameList.clear();
		return frames;
	}

}
