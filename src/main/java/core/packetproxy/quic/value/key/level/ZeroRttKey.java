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

import lombok.EqualsAndHashCode;
import lombok.Value;
import packetproxy.quic.value.key.Key;

@EqualsAndHashCode(callSuper = true)
@Value
public class ZeroRttKey extends Key {

	public static ZeroRttKey of(byte[] secret) {
		Key key = Key.of(secret);
		return new ZeroRttKey(key.getSecret(), key.getKey(), key.getIv(), key.getHp());
	}

	public ZeroRttKey(byte[] secret, byte[] key, byte[] iv, byte[] hp) {
		super(secret, key, iv, hp);
	}

}
