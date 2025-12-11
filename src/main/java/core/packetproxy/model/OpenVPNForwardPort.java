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
package packetproxy.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "openvpn_forward_ports")
public class OpenVPNForwardPort {

	public enum TYPE {
		TCP("tcp"), UDP("udp");

		private final String proto;

		private TYPE(String proto) {
			this.proto = proto;
		}

		public String toString() {
			return this.proto;
		}
	}

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField(uniqueCombo = true)
	private TYPE type;

	@DatabaseField(uniqueCombo = true)
	private int fromPort;

	@DatabaseField(uniqueCombo = true)
	private int toPort;

	public OpenVPNForwardPort() {
		// ORMLite needs a no-arg constructor
	}

	public OpenVPNForwardPort(TYPE type, int fromPort, int toPort) {
		this.type = type;
		this.fromPort = fromPort;
		this.toPort = toPort;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public void setType(TYPE type) {
		this.type = type;
	}

	public TYPE getType() {
		return this.type;
	}

	public void setFromPort(int fromPort) {
		this.fromPort = fromPort;
	}

	public int getFromPort() {
		return this.fromPort;
	}

	public void setToPort(int toPort) {
		this.toPort = toPort;
	}

	public int getToPort() {
		return this.toPort;
	}

	@Override
	public int hashCode() {
		return this.getId();
	}

	public boolean equals(OpenVPNForwardPort obj) {
		return this.getId() == obj.getId();
	}
}
