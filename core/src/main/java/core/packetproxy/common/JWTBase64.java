/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.common;

import org.apache.commons.codec.binary.Base64;

public class JWTBase64 extends JWT {

	public JWTBase64(JWT jwt) {
		super(jwt);
	}

	public JWTBase64(String jwtString) {
		String[] jwtPart = jwtString.split("\\.", 3);
		header = new String(Base64.decodeBase64(jwtPart[0]));
		payload = new String(Base64.decodeBase64(jwtPart[1]));
	}

	@Override
	public String toJwtString() throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(createHeader(this.header));
		sb.append(".");
		sb.append(createPayload(this.payload));
		String header_payload = sb.toString();
		String signature = createSignature(header_payload);
		sb.append(".");
		if (!signature.isEmpty()) {
			sb.append(signature);
		}
		return sb.toString();
	}

	@Override
	protected String createSignature(String input) throws Exception {
		return "NotDefined";
	}

	@Override
	protected String createHeader(String input) throws Exception {
		return Base64.encodeBase64URLSafeString(input.getBytes());
	}

	@Override
	protected String createPayload(String input) throws Exception {
		return Base64.encodeBase64URLSafeString(input.getBytes());
	}
}
