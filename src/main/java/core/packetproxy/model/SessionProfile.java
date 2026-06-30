/*
 * Copyright 2026 DeNA Co., Ltd.
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

@DatabaseTable(tableName = "session_profiles")
public class SessionProfile {

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField(unique = true)
	private String name;

	@DatabaseField
	private String authorization;

	@DatabaseField
	private String cookie;

	public SessionProfile() {
		// ORMLite needs a no-arg constructor
	}

	public SessionProfile(String name, String authorization) {
		this.name = name;
		this.authorization = authorization;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAuthorization() {
		return authorization;
	}

	public void setAuthorization(String authorization) {
		this.authorization = authorization;
	}

	public String getCookie() {
		return cookie;
	}

	public void setCookie(String cookie) {
		this.cookie = cookie;
	}

	public static String formatAuthorizationPreview(String authorization) {
		if (authorization == null || authorization.isEmpty()) {
			return "(none)";
		}
		if (authorization.length() <= 20) {
			return authorization;
		}
		return authorization.substring(0, 20) + "...";
	}
}
