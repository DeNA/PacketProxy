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

package packetproxy.quic.value;

import java.security.SecureRandom;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;

@Value(staticConstructor = "of")
public class Token {

	public static Token generateRandom(int size) {
		byte[] token = new byte[size];
		new SecureRandom().nextBytes(token);
		return new Token(token);
	}

	@Getter(AccessLevel.NONE)
	byte[] token;

	public byte[] getBytes() {
		return token;
	}

	@Override
	public String toString() {
		return String.format("Token([%s])", Hex.encodeHexString(this.token));
	}
}
