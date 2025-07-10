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

package packetproxy.http3.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import packetproxy.quic.value.StreamId;

class Http3Test {

	@Test
	void HttpとHttpRawが相互に変換できること() throws Exception {
		Http3 http3 = new Http3();

		HttpRaw testHttpRaw = HttpRaw.of(StreamId.of(0),
				Hex.decodeHex(
						"0000d1508b9c475cbe474d612af5153fd7518860d5485f2bce9a685f5088c6cfe96c3b015c1f".toCharArray()),
				new byte[]{});

		byte[] http = http3.generateReqHttp(testHttpRaw); // HttpRaw → Http に変換
		HttpRaw httpRaw = http3.generateHttpRaw(http); // Http → HttpRaw に変換

		assertThat(httpRaw.getEncodedHeader()).isEqualTo(testHttpRaw.getEncodedHeader());
	}

}
