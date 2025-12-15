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

package packetproxy.quic.value.key.level;

import at.favre.lib.hkdf.HKDF;
import lombok.EqualsAndHashCode;
import lombok.Value;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.ConnectionId;
import packetproxy.quic.value.key.Key;

@EqualsAndHashCode(callSuper = true)
@Value
public class InitialKey extends Key {

	public static InitialKey of(Constants.Role role, ConnectionId destConnId) {
		HKDF hkdf = HKDF.fromHmacSha256();
		byte[] initialSecret = hkdf.extract(STATIC_SALT_V1, destConnId.getBytes());
		byte[] secret = (role == Constants.Role.CLIENT)
				? hkdfExpandLabel(initialSecret, "client in", "", (short) 32)
				: hkdfExpandLabel(initialSecret, "server in", "", (short) 32);
		Key key = Key.of(secret);
		return new InitialKey(key.getSecret(), key.getKey(), key.getIv(), key.getHp());
	}

	public InitialKey(byte[] secret, byte[] key, byte[] iv, byte[] hp) {
		super(secret, key, iv, hp);
	}
}
