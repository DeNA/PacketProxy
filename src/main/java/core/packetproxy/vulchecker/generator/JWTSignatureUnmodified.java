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

package packetproxy.vulchecker.generator;

import org.apache.commons.lang3.StringUtils;
import packetproxy.common.JWTBase64;

public class JWTSignatureUnmodified extends JWTBase64 {

	String signature;

	public JWTSignatureUnmodified(String jwtString) {
		super(jwtString);
		signature = StringUtils.substringAfterLast(jwtString, ".");
	}

	@Override
	protected String createSignature(String input) throws Exception {
		return signature;
	}
}
