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

import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Value;
import packetproxy.quic.value.frame.AckFrame;
import packetproxy.quic.value.frame.Frame;

@Value
public class Frames implements Iterable<Frame> {

	public static final Frames empty = new Frames(ImmutableList.of());

	public static Frames parse(byte[] bytes) {
		return Frames.parse(ByteBuffer.wrap(bytes));
	}

	public static Frames parse(ByteBuffer buffer) {
		try {
			List<Frame> frames = new ArrayList<>();
			while (buffer.remaining() > 0) {
				Frame frame = FrameParser.create(buffer);
				frames.add(frame);
			}
			return new Frames(frames);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Frames.empty;
	}

	public static Frames of(Frame e1) {
		return new Frames(ImmutableList.of(e1));
	}

	public static Frames of(Frame e1, Frame e2) {
		return new Frames(ImmutableList.of(e1, e2));
	}

	public static Frames of(Frame e1, Frame e2, Frame e3) {
		return new Frames(ImmutableList.of(e1, e2, e3));
	}

	public static Frames of(List<Frame> frameList) {
		return new Frames(new ArrayList<>(frameList));
	}

	List<Frame> frames;

	public boolean isAckEliciting() {
		return frames.stream().anyMatch(Frame::isAckEliciting);
	}

	public boolean hasAckFrame() {
		return frames.stream().anyMatch(frame -> frame instanceof AckFrame);
	}

	public Optional<AckFrame> getAckFrame() {
		return frames.stream().filter(f -> f instanceof AckFrame).map(f -> (AckFrame) f).findFirst();
	}

	public String toString() {
		return String.format("Frames(%s)", frames.stream().map(Object::toString).collect(Collectors.joining(",")));
	}

	@Override
	public Iterator<Frame> iterator() {
		return frames.iterator();
	}

}
