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

package packetproxy.quic.service.pnspace.helper;

import java.util.*;
import lombok.Getter;
import packetproxy.quic.service.frame.Frames;
import packetproxy.quic.value.frame.Frame;

@Getter
public class SendFrameQueue {

	Deque<Frame> frames;

	public SendFrameQueue() {
		this.frames = new ArrayDeque<>();
	}

	public synchronized void add(Frame frame) {
		this.frames.add(frame);
	}

	public synchronized void add(Frames frames) {
		this.frames.addAll(frames.getFrames());
	}

	public synchronized List<Frame> pollAll() {
		List<Frame> frames = new ArrayList<>();
		for (Frame frame = this.frames.poll(); frame != null; frame = this.frames.poll()) {
			frames.add(frame);
		}
		return frames;
	}

	public synchronized void clear() {
		this.frames.clear();
	}

}
