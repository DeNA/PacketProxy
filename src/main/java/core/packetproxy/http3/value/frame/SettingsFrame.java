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

package packetproxy.http3.value.frame;
import static packetproxy.util.Logging.errWithStackTrace;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import lombok.Value;
import packetproxy.http3.value.Setting;
import packetproxy.http3.value.SettingParam;
import packetproxy.quic.value.SimpleBytes;
import packetproxy.quic.value.VariableLengthInteger;

@Value
public class SettingsFrame implements Frame {

	public static final long TYPE = 0x04;

	public static List<Long> supportedTypes() {
		return ImmutableList.of(TYPE);
	}

	public static SettingsFrame generateSettingsFrameWithDefaultValue() {
		return new SettingsFrame(Setting.generateWithDefaultValue());
	}

	public static SettingsFrame of(Setting setting) {
		return new SettingsFrame(setting);
	}

	public static SettingsFrame parse(ByteBuffer buffer) throws Exception {
		long frameType = VariableLengthInteger.parse(buffer).getValue();
		long frameLength = VariableLengthInteger.parse(buffer).getValue();
		byte[] frameData = SimpleBytes.parse(buffer, frameLength).getBytes();

		ByteBuffer settingsDataBuffer = ByteBuffer.wrap(frameData);

		Setting.SettingBuilder settingBuilder = Setting.builder();
		while (settingsDataBuffer.hasRemaining()) {

			long id = VariableLengthInteger.parse(settingsDataBuffer).getValue();
			long value = VariableLengthInteger.parse(settingsDataBuffer).getValue();
			if (SettingParam.QpackMaxTableCapacity.idEqualsTo(id)) {

				settingBuilder = settingBuilder.qpackMaxTableCapacity(value);
			} else if (SettingParam.MaxFieldSectionSize.idEqualsTo(id)) {

				settingBuilder = settingBuilder.maxFieldSectionSize(value);
			} else if (SettingParam.QpackBlockedStreams.idEqualsTo(id)) {

				settingBuilder = settingBuilder.qpackBlockedStreams(value);
			} else if (SettingParam.EnableConnectProtocol.idEqualsTo(id)) {

				settingBuilder = settingBuilder.enableConnectProtocol(value);
			} else if (SettingParam.H3Datagram.idEqualsTo(id)) {

				settingBuilder = settingBuilder.h3Datagram(value);
			} else if (SettingParam.H3DatagramOld.idEqualsTo(id)) {

				settingBuilder = settingBuilder.h3DatagramOld(value);
			} else if (SettingParam.EnableMetaData.idEqualsTo(id)) {

				settingBuilder = settingBuilder.enableMetaData(value);
			} else {

				// Grease Setting. Just ignored.
			}
		}
		return new SettingsFrame(settingBuilder.build());
	}

	long type;
	Setting setting;

	private SettingsFrame(Setting setting) {
		this.type = TYPE;
		this.setting = setting;
	}

	@Override
	public byte[] getBytes() {
		ByteArrayOutputStream frameStream = new ByteArrayOutputStream();
		try {

			ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

			if (this.setting.getQpackMaxTableCapacity() != SettingParam.QpackMaxTableCapacity.defaultValue) {

				dataStream.write(VariableLengthInteger.of(SettingParam.QpackMaxTableCapacity.getId()).getBytes());
				dataStream.write(VariableLengthInteger.of(this.setting.getQpackMaxTableCapacity()).getBytes());
			}
			if (this.setting.getQpackBlockedStreams() != SettingParam.QpackBlockedStreams.defaultValue) {

				dataStream.write(VariableLengthInteger.of(SettingParam.QpackBlockedStreams.getId()).getBytes());
				dataStream.write(VariableLengthInteger.of(this.setting.getQpackBlockedStreams()).getBytes());
			}
			if (this.setting.getMaxFieldSectionSize() != SettingParam.MaxFieldSectionSize.defaultValue) {

				dataStream.write(VariableLengthInteger.of(SettingParam.MaxFieldSectionSize.getId()).getBytes());
				dataStream.write(VariableLengthInteger.of(this.setting.getMaxFieldSectionSize()).getBytes());
			}
			if (this.setting.getEnableConnectProtocol() != SettingParam.EnableConnectProtocol.defaultValue) {

				dataStream.write(VariableLengthInteger.of(SettingParam.EnableConnectProtocol.getId()).getBytes());
				dataStream.write(VariableLengthInteger.of(this.setting.getEnableConnectProtocol()).getBytes());
			}
			if (this.setting.getH3Datagram() != SettingParam.H3Datagram.defaultValue) {

				dataStream.write(VariableLengthInteger.of(SettingParam.H3Datagram.getId()).getBytes());
				dataStream.write(VariableLengthInteger.of(this.setting.getH3Datagram()).getBytes());
			}
			if (this.setting.getH3DatagramOld() != SettingParam.H3DatagramOld.defaultValue) {

				dataStream.write(VariableLengthInteger.of(SettingParam.H3DatagramOld.getId()).getBytes());
				dataStream.write(VariableLengthInteger.of(this.setting.getH3DatagramOld()).getBytes());
			}
			if (this.setting.getEnableMetaData() != SettingParam.EnableMetaData.defaultValue) {

				dataStream.write(VariableLengthInteger.of(SettingParam.EnableMetaData.getId()).getBytes());
				dataStream.write(VariableLengthInteger.of(this.setting.getEnableMetaData()).getBytes());
			}

			byte[] data = dataStream.toByteArray();
			frameStream.write(VariableLengthInteger.of(this.type).getBytes());
			frameStream.write(VariableLengthInteger.of(data.length).getBytes());
			frameStream.write(data);

		} catch (Exception e) {

			errWithStackTrace(e);
		}

		return frameStream.toByteArray();
	}
}
