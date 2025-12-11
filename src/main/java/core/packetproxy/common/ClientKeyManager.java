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

import static packetproxy.util.Logging.errWithStackTrace;

import java.util.HashMap;
import java.util.List;
import javax.net.ssl.KeyManager;
import packetproxy.model.ClientCertificate;
import packetproxy.model.ClientCertificates;
import packetproxy.model.Server;

/** KeyManager Class for Client Certificate */
public class ClientKeyManager {

	private static HashMap<Integer, KeyManager[]> keyManagersHashMap = new HashMap<>();

	/**
	 * Initialize ClientKeyManager at launch
	 *
	 * @throws Exception:
	 *             Failed to Get Instance of ClientCertificates
	 */
	public static void initialize() throws Exception {
		List<ClientCertificate> certificateList = ClientCertificates.getInstance().queryEnabled();
		for (ClientCertificate cert : certificateList) {

			try {

				setKeyManagers(cert.getServer(), cert.load());
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		}
	}

	/**
	 * Set a pair of Applied Server and KeyManagers
	 *
	 * @param server:
	 *            Applied Server
	 * @param keyManagers:
	 *            KeyManagers
	 */
	public static void setKeyManagers(Server server, KeyManager[] keyManagers) {
		if (server != null)
			keyManagersHashMap.put(server.getId(), keyManagers);
	}

	/**
	 * Get KeyManagers from Server You Want to Get
	 *
	 * @param server:
	 *            Server You Want to Get KeyManagers
	 * @return KeyManagers for Applied Server or null
	 */
	public static KeyManager[] getKeyManagers(Server server) {
		return server != null ? keyManagersHashMap.get(server.getId()) : null;
	}

	/**
	 * Remove KeyManagers of The Server
	 *
	 * @param server:
	 *            Server You Want to Remove
	 */
	public static void removeKeyManagers(Server server) {
		if (server != null)
			keyManagersHashMap.remove(server.getId());
	}
}
