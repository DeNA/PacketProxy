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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettingTest {
	@Test
	void Builderが動作すること() {
		Setting setting = Setting.builder().enableMetaData(1).qpackMaxTableCapacity(100).build();
		assertThat(setting.getEnableMetaData()).isEqualTo(1);
		assertThat(setting.getQpackMaxTableCapacity()).isEqualTo(100);
		assertThat(setting.getH3Datagram()).isEqualTo(0);
		assertThat(setting.getEnableConnectProtocol()).isEqualTo(0);
		assertThat(setting.getMaxFieldSectionSize()).isEqualTo(Long.MAX_VALUE);
	}

}
