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

package packetproxy.quic.service.transportparameter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.utils.Constants;

class TransportParametersTest {

	@Test
	public void smoke() throws Exception {
		byte[] test = Hex.decodeHex(
				"0039005505048020000004048010000008024201010480007530030245a00902420106048001006307048000ffff0e01080b010a0f087c67f19599d5b680537b0480004fb0c0000000ff02de1a0243e88000715801036ab200"
						.toCharArray());
		TransportParameters params = new TransportParameters(Constants.Role.CLIENT, test);
		System.out.println(params);
		assertEquals(2097152, params.getInitMaxStreamDataBidiLocal());
		assertEquals(1048576, params.getInitMaxData());
	}
}
