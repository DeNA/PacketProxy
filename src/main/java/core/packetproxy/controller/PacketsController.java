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
package packetproxy.controller;

import packetproxy.model.Packet;
import packetproxy.model.Packets;

public class PacketsController {

	private static PacketsController instance;

	public static PacketsController getinstance() throws Exception {
		if (instance == null) {

			instance = new PacketsController();
		}
		return instance;
	}

	private Packets packets;

	private PacketsController() throws Exception {
		packets = Packets.getInstance();
	}

	public void add(Packet packet) throws Exception {
		if (packet == null)
			return;
		packets.create(packet);
		packets.firePropertyChange();
	}

}
