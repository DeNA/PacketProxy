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
package packetproxy.common;

import org.apache.commons.io.FilenameUtils;
import packetproxy.model.Database;

public final class ProjectDisplayName {

	private static final String UNKNOWN = "Unknown";
	private static final String DEFAULT = "Default";
	private static final String TEMPORARY = "Temporary";

	private ProjectDisplayName() {
	}

	public static String get() {
		try {
			var dbPath = Database.getInstance().getDatabasePath();
			if (dbPath == null) {

				return UNKNOWN;
			}
			return fromFileName(dbPath.getFileName().toString());
		} catch (Exception e) {

			return UNKNOWN;
		}
	}

	static String fromFileName(String fileName) {
		if (fileName.equals("resources.sqlite3")) {

			return DEFAULT;
		}
		if (fileName.equals("resources_temp.sqlite3")) {

			return TEMPORARY;
		}
		if (fileName.startsWith("packetproxy-") && fileName.matches("packetproxy-\\d{8}-\\d{6}\\.sqlite3")) {

			return TEMPORARY;
		}
		return FilenameUtils.removeExtension(fileName);
	}
}
