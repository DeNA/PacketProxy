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
package packetproxy.model.CAs;

import java.security.KeyStore;

public class SelfSignedCA extends CA {
	private static final String name = "Temp CA (for SelfSigned Test)";
	private static final String desc = "テンポラリCA証明書（オレオレ証明書の検証の為）";
	private static final String keyStorePath = "/certificates/user.ks";

	public SelfSignedCA() throws Exception {
		super.loadFromResource(keyStorePath);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getUTF8Name() {
		return desc;
	}

	@Override
	public KeyStore createKeyStore(String commonName, String[] domainNames) throws Exception {
		return super.createKeyStore(commonName, domainNames);
	}

	@Override
	public String toString() {
		return "SelfSignedCA [name=" + name + ", desc=" + desc + "]";
	}

}
