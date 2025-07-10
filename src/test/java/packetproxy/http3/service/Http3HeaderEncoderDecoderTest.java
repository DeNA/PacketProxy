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

package packetproxy.http3.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.junit.jupiter.api.Test;
import packetproxy.http3.helper.Http3TestHelper;

class Http3HeaderEncoderDecoderTest {

	@Test
	public void ヘッダをencode後decodeできること() throws Exception {
		Http3HeaderEncoder encoder = new Http3HeaderEncoder(1000);
		Http3HeaderDecoder decoder = new Http3HeaderDecoder();

		MetaData inputHeader = Http3TestHelper.generateTestMetaData();

		byte[] headerEncoded = encoder.encode(0, inputHeader);
		byte[] headerInsts = encoder.getInstructions();

		decoder.putInstructions(headerInsts);
		decoder.decode(0, headerEncoded).forEach(outputHeader -> {

			HttpFields fields = outputHeader.getFields();
			System.out.println(fields);
		});
		byte[] headerInstsFromDecoder = decoder.getInstructions();

		encoder.putInstructions(headerInstsFromDecoder);
	}

	@Test
	public void getInstructionsすると命令が消費されること() throws Exception {
		Http3HeaderEncoder encoder = new Http3HeaderEncoder(1000);
		MetaData inputHeader = Http3TestHelper.generateTestMetaData();
		byte[] headerEncoded = encoder.encode(0, inputHeader);
		assertThat(encoder.getInstructions()).isNotEmpty();
		assertThat(encoder.getInstructions()).isEmpty();
	}

	@Test
	public void capacityのデバッグ() {
		System.out.println(Hex.encodeHexString(new Http3HeaderEncoder(0).getInstructions()));
		System.out.println(Hex.encodeHexString(new Http3HeaderEncoder(1).getInstructions()));
		System.out.println(Hex.encodeHexString(new Http3HeaderEncoder(10).getInstructions()));
		System.out.println(Hex.encodeHexString(new Http3HeaderEncoder(1000).getInstructions()));
	}

	@Test
	public void サンプルデータをデコードする() throws Exception {
		byte[] bytes = Hex.decodeHex(
				"0000d1d7510b2f696e6465782e68746d6c500f68326f2e6578616d7031652e6e65745f500a48335a65726f2f312e30"
						.toCharArray());
		Http3HeaderDecoder decoder = new Http3HeaderDecoder();
		decoder.decode(0, bytes).forEach(outputHeader -> {

			if (outputHeader.isRequest()) {

				MetaData.Request request = (MetaData.Request) outputHeader;
				System.out.printf("%s %s %s%n", request.getMethod(), request.getURI().getPath(),
						request.getHttpVersion());
				System.out.printf("host: %s%n", request.getURI().getHost());
				request.getFields().forEach(field -> {

					System.out.printf("%s: %s%n", field.getName(), field.getValue());
				});
			}
		});
		assertThat(decoder.getInstructions().length).isEqualTo(0);
	}

}
