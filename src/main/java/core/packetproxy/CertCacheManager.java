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
package packetproxy;

import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import packetproxy.model.CAs.CA;

public class CertCacheManager {
	private static CertCacheManager instance;
	
	public static CertCacheManager getInstance() throws Exception {
		if (instance == null) {
			instance = new CertCacheManager();
		}
		return instance;
	}
	
	private Map<String,KeyStore> certCache;
	
	private CertCacheManager() throws Exception {
		certCache = new HashMap<String,KeyStore>();
	}
	
	public KeyStore getKeyStore(String commonName, String[] domainNames, CA ca) throws Exception {
		synchronized (instance) {
			String key = commonName;
			key += Arrays.stream(domainNames).collect(Collectors.joining());
			key += ca.getName();
			KeyStore ks = certCache.get(key);
			if (ks != null) {
				return ks;
			}
			ks = ca.createKeyStore(commonName, domainNames);
			certCache.put(key, ks);
			return ks;
		}
	}

}
