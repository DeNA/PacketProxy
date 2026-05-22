/*
 * Copyright 2019,2022 DeNA Co., Ltd.
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
import java.util.Optional;
import packetproxy.model.CAs.CA;

@DatabaseTable(tableName = "listenports")
public class ListenPort {

	public enum Protocol {
		TCP, UDP
	}

	public enum TYPE {
		HTTP_PROXY, FORWARDER, SSL_FORWARDER, UDP_FORWARDER, SSL_TRANSPARENT_PROXY, HTTP_TRANSPARENT_PROXY, XMPP_SSL_FORWARDER, QUIC_FORWARDER, QUIC_TRANSPARENT_PROXY;

		public boolean isForwarder() {
			return this == FORWARDER || this == SSL_FORWARDER || this == UDP_FORWARDER || this == XMPP_SSL_FORWARDER
					|| this == QUIC_FORWARDER;
		}

		public Protocol getProtocol() {
			if (this == QUIC_FORWARDER || this == QUIC_TRANSPARENT_PROXY || this == UDP_FORWARDER) {

				return Protocol.UDP;
			} else {

				return Protocol.TCP;
			}
		}
	}

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField
	private Boolean enabled;
	@DatabaseField
	private String ca_name;

	@DatabaseField(uniqueCombo = true)
	private int port;

	@DatabaseField(uniqueCombo = true)
	private TYPE type;

	@DatabaseField(uniqueCombo = true)
	private int server_id;

	private Protocol protocol = null;

	public ListenPort() {
		// ORMLite needs a no-arg constructor
	}

	public ListenPort(int port, TYPE type) {
		this.enabled = false;
		this.port = port;
		this.type = type;
		this.server_id = 0;
		this.ca_name = "PacketProxy CA";
		this.protocol = type.getProtocol();
	}

	public ListenPort(int port, TYPE type, Server server, String ca_name) {
		this.enabled = false;
		this.port = port;
		this.type = type;
		this.server_id = (server != null) ? server.getId() : 0;
		this.ca_name = ca_name;
		this.protocol = type.getProtocol();
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled() {
		this.enabled = true;
	}

	public void setDisabled() {
		this.enabled = false;
	}

	public void setCA(CA ca) {
		this.ca_name = ca.getName();
	}

	public Optional<CA> getCA() {
		return CAFactory.find(this.ca_name);
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getServerId() {
		return this.server_id;
	}

	public void setServerId(int server_id) {
		this.server_id = server_id;
	}

	public Server getServer() throws Exception {
		return Servers.getInstance().query(this.server_id);
	}

	public Protocol getProtocol() {
		if (this.protocol == null) {

			this.protocol = this.type.getProtocol();
		}
		return this.protocol;
	}

	public String getProtoPort() {
		return String.format("%s %s", getProtocol(), getPort());
	}

	public TYPE getType() {
		return this.type;
	}

	public void setType(TYPE type) {
		this.type = type;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	@Override
	public int hashCode() {
		return this.getId();
	}

	public boolean equals(ListenPort obj) {
		return this.getId() == obj.getId();
	}
}
