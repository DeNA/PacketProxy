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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DuplexManager {

	private static DuplexManager instance;

	public static DuplexManager getInstance() throws Exception {
		if (instance == null) {

			instance = new DuplexManager();
		}
		return instance;
	}

	private Map<Integer, Duplex> duplex_list;

	public DuplexManager() {
		duplex_list = new HashMap<Integer, Duplex>();
	}

	public void closeAndClearDuplex(int listenPort) throws Exception {
		for (Iterator<Integer> i = duplex_list.keySet().iterator(); i.hasNext();) {

			int key = i.next();
			Duplex d = duplex_list.get(key);
			if (d.isListenPort(listenPort)) {

				d.close();
				i.remove();
			}
		}
	}

	public int registerDuplex(Duplex duplex) {
		duplex_list.put(duplex.hashCode(), duplex);
		return duplex.hashCode();
	}

	public Duplex getDuplex(int hash) {
		return duplex_list.get(hash);
	}

	public boolean has(int hash) {
		return (duplex_list.get(hash) == null) ? false : true;
	}

}
