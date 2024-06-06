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
package packetproxy.http2;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import packetproxy.http2.frames.Frame;

public class StreamManager {
	private Map<Integer, List<Frame>> streamMap = new HashMap<>();

	public StreamManager() {
	}

	public void write(Frame frame) {
		List<Frame> stream = streamMap.get(frame.getStreamId());
		if (stream == null) {
			stream = new LinkedList<Frame>();
			streamMap.put(frame.getStreamId(), stream);
		}
		stream.add(frame);
	}

	public List<Frame> read(int streamId) {
		return streamMap.get(streamId);
	}

	public Set<Map.Entry<Integer, List<Frame>>> entrySet() {
		return streamMap.entrySet();
	}

	public void clear(int streamId) {
		streamMap.remove(streamId);
	}

	public byte[] mergePayload(int streamId) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame : read(streamId)) {
			out.write(frame.getPayload());
		}
		return out.toByteArray();
	}

	public byte[] toByteArray(int streamId) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame : read(streamId)) {
			out.write(frame.toByteArray());
		}
		return out.toByteArray();
	}

	// streamIdは任意で1つフレームを返して削除する
	// frameが1つもない場合はnullを返す
	public Frame popOneFrame() {
		if (streamMap.size() == 0) {
			return null;
		}
		Integer streamId = streamMap.keySet().stream().findFirst().get();
		return popOneFrame(streamId);
	}

	// 指定したstreamIdから1つフレームを返して削除する
	public Frame popOneFrame(int streamId) {
		Frame frame = streamMap.get(streamId).remove(0);
		if (streamMap.get(streamId).size() == 0) {
			clear(streamId);
		}
		return frame;
	}
}
