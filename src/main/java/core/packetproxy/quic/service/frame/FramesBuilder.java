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

package packetproxy.quic.service.frame;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.frame.Frame;
import packetproxy.quic.value.frame.PaddingFrame;

public class FramesBuilder {
	List<Frame> frames = new ArrayList<>();

	public FramesBuilder add(Frame frame) {
		frames.add(frame);
		return this;
	}

	/*
	 * Clients MUST ensure that UDP datagrams containing Initial packets have UDP
	 * payloads of at least 1200 bytes, adding PADDING frames as necessary.
	 */
	public FramesBuilder addPaddingFramesToEnsure1200Bytes() {
		long currentBytesLength = getBytes().length;
		long paddingFrameLength = 1200 - currentBytesLength;
		if (paddingFrameLength > 0) {
			this.add(new PaddingFrame(paddingFrameLength));
		}
		return this;
	}

	public byte[] getBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(1500);
		for (Frame frame : frames) {
			buffer.put(frame.getBytes());
		}
		buffer.flip();
		return SimpleBytes.parse(buffer, buffer.remaining()).getBytes();
	}

	public Frames build() {
		return new Frames(frames);
	}
}
