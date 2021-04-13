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

@DatabaseTable(tableName = "sslpassthroughs")
public class SSLPassThrough
{
	public static final int ALL_PORTS = -1;

	@DatabaseField(generatedId = true)
	private int id;
	@DatabaseField
	private Boolean enabled;
	@DatabaseField(uniqueCombo = true)
	private String server_name;
	@DatabaseField(uniqueCombo = true)
	private int listen_port;

	public SSLPassThrough() {
		// ORMLite needs a no-arg constructor 
	}
	public SSLPassThrough(String server_name, int listen_port) throws Exception {
		setEnabled();
		setServerName(server_name);
		setListenPort(listen_port);
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
	public String getServerName() {
		return this.server_name;
	}
	public void setServerName(String server_name) {
		this.server_name = server_name;
	}
	public int getListenPort() throws Exception {
		return listen_port;
	}
	public void setListenPort(int listen_port) throws Exception {
		this.listen_port = listen_port;
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
	public boolean equals(SSLPassThrough obj) {
		return this.getId() == obj.getId() ? true : false;
	}
}
