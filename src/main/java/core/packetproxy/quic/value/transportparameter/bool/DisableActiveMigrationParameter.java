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

package packetproxy.quic.value.transportparameter.bool;

import java.nio.ByteBuffer;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import packetproxy.quic.value.transportparameter.TransportParameter;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Value
public class DisableActiveMigrationParameter extends TransportParameter {

	public static final long ID = 0xc;

	public DisableActiveMigrationParameter(ByteBuffer buffer) {
		super(buffer);
	}

	public DisableActiveMigrationParameter() {
		super(ID, 0, new byte[0]);
	}

}
