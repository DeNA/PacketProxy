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

package packetproxy.http3.value;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Setting {

    public static Setting generateWithDefaultValue() {
        return Setting.builder().build();
    }
    public static class SettingBuilder {
        long qpackMaxTableCapacity = SettingParam.QpackMaxTableCapacity.defaultValue;
        long maxFieldSectionSize = SettingParam.MaxFieldSectionSize.defaultValue;
        long qpackBlockedStreams = SettingParam.QpackBlockedStreams.defaultValue;
        long enableConnectProtocol = SettingParam.EnableConnectProtocol.defaultValue;
        long h3Datagram = SettingParam.H3Datagram.defaultValue;
        long h3DatagramOld = SettingParam.H3DatagramOld.defaultValue;
        long enableMetaData = SettingParam.EnableMetaData.defaultValue;
    }

    long qpackMaxTableCapacity;
    long maxFieldSectionSize;
    long qpackBlockedStreams;
    long enableConnectProtocol;
    long h3Datagram;
    long h3DatagramOld;
    long enableMetaData;
}
