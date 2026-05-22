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

@DatabaseTable(tableName = "resolutions")
public class Resolution {

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField(uniqueCombo = true)
	private String ip;

	@DatabaseField(uniqueCombo = true)
	private String hostname;

	@DatabaseField
	private boolean enabled;
	@DatabaseField
	private String comment;

	public Resolution() {
		// ORMLite needs a no-arg constructor
	}

	public Resolution(String ip, String hostname) {
		initialize(ip, hostname, false, "");
	}

	public Resolution(String ip, String hostname, boolean enabled, String comment) {
		initialize(ip, hostname, enabled, comment);
	}

	private void initialize(String ip, String hostname, boolean enabled, String comment) {
		this.ip = ip;
		this.hostname = hostname;
		this.enabled = enabled;
		this.comment = comment;
	}

	@Override
	public String toString() {
		return String.format("%s to %s", ip, hostname);
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getIp() {
		return this.ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getHostName() {
		return hostname;
	}

	public void setHostName(String hostname) {
		this.hostname = hostname;
	}

	public void enableResolution() {
		this.enabled = true;
	}

	public void disableResolution() {
		this.enabled = false;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getComment() {
		return this.comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}
}
