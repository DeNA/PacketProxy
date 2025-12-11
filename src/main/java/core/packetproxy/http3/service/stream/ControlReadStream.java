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

import static packetproxy.util.Throwing.rethrow;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.http3.service.frame.FrameParser;
import packetproxy.http3.value.Setting;
import packetproxy.http3.value.frame.Frames;
import packetproxy.http3.value.frame.GreaseFrame;
import packetproxy.http3.value.frame.SettingsFrame;
import packetproxy.quic.value.QuicMessage;
import packetproxy.quic.value.StreamId;

public class ControlReadStream extends Stream implements ReadStream {

	private boolean hasWrite = false;
	private final Frames frames = Frames.emptyList();
	private Setting setting = null;

	public ControlReadStream(StreamId streamId) {
		super(streamId, StreamType.ControlStreamType);
	}

	public void write(QuicMessage msg) throws Exception {
		byte[] targetData = msg.getData();
		if (!this.hasWrite) {

			if (!super.streamTypeEquals(targetData[0])) {

				throw new Exception(String.format("Error: Not start with %x on http3 control stream. (actual: %x)",
						super.streamType.type, targetData[0]));
			}
			targetData = ArrayUtils.subarray(targetData, 1, targetData.length);
			this.hasWrite = true;
		}
		Frames frames = FrameParser.parse(targetData);
		frames.forEach(rethrow(frame -> {
			if (frame instanceof SettingsFrame) {

				SettingsFrame settingFrame = (SettingsFrame) frame;
				this.setting = settingFrame.getSetting();
			} else if (frame instanceof GreaseFrame) {

				// just ignored
			} else {

				throw new Exception(String.format("Error: add non-SettingsFrame into ControlStream: %s", frame));
			}
			this.frames.add(frame);
		}));
	}

	@Override
	public byte[] readAllBytes() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		this.frames.forEach(rethrow(frame -> {
			bytes.write(frame.getBytes());
		}));
		this.frames.clear();
		return bytes.toByteArray();
	}

	public Optional<Setting> getSetting() {
		return this.setting != null ? Optional.of(this.setting) : Optional.empty();
	}
}
