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

import packetproxy.util.PacketProxyUtility;

// ローカルPC の現在時刻（ミリ秒単位）を利用したユニークな自動採番クラス
// かならず昇順の番号を採番するため、ソートするのに便利
public class UniqueID {

	private static UniqueID instance;

	public static UniqueID getInstance() throws Exception {
		if (instance == null) {
			instance = new UniqueID();
		}
		return instance;
	}

	private long lastId = 0;

	private UniqueID() throws Exception {
		lastId = getNow();
	}

	public synchronized long createId() throws Exception {
		while (true) {
			long newId = getNow();
			if (newId == lastId) {
				Thread.sleep(1);
				continue;
			} else if (newId < lastId) {
				PacketProxyUtility.getInstance().packetProxyLogErr("Time of your pc seemed to be changed...");
			}
			return lastId = newId;
		}
	}

	private long getNow() throws Exception {
		return System.currentTimeMillis();
	}
}
