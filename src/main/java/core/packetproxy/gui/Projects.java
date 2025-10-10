/*
 * Copyright 2025 DeNA Co., Ltd.
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
package packetproxy.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import packetproxy.common.I18nString;
import packetproxy.common.RecentProjectsStore;
import packetproxy.model.Database;

public class Projects {

	public static class ProjectInfo {
		private final String path;
		private final String name;
		private final String lastModified;
		private final long lastModifiedMillis;

		public ProjectInfo(String path) {
			this.path = path;
			var p = Paths.get(path);
			this.name = extractProjectName(p);
			this.lastModifiedMillis = getLastModifiedMillis(p);
			this.lastModified = formatLastModified(p);
		}

		public String getPath() {
			return path;
		}

		public String getName() {
			return name;
		}

		public String getLastModified() {
			return lastModified;
		}

		public long getLastModifiedMillis() {
			return lastModifiedMillis;
		}

		@Override
		public String toString() {
			return name;
		}

		private String extractProjectName(Path path) {
			var fileName = path.getFileName().toString();
			return FilenameUtils.removeExtension(fileName);
		}

		private long getLastModifiedMillis(Path path) {
			try {
				return Files.getLastModifiedTime(path).toMillis();
			} catch (Exception e) {
				return 0L;
			}
		}

		private String formatLastModified(Path path) {
			try {
				var fileTime = Files.getLastModifiedTime(path);
				var sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				return sdf.format(new Date(fileTime.toMillis()));
			} catch (Exception e) {
				return I18nString.get("Unknown");
			}
		}
	}

	public List<ProjectInfo> getValidRecentProjects() throws Exception {
		var recents = new ArrayList<>(RecentProjectsStore.load());
		var validRecents = new ArrayList<ProjectInfo>();
		var validPaths = new ArrayList<String>();

		for (var path : recents) {
			if (Files.exists(Paths.get(path))) {
				validRecents.add(new ProjectInfo(path));
				validPaths.add(path);
			}
		}

		if (validPaths.size() != recents.size()) {
			RecentProjectsStore.save(validPaths);
		}

		validRecents.sort((a, b) -> Long.compare(b.getLastModifiedMillis(), a.getLastModifiedMillis()));

		return validRecents;
	}

	public void openProject(String path) throws Exception {
		Database.getInstance().openAt(path);
		RecentProjectsStore.add(Paths.get(path));
	}

	public String createTemporaryProject() throws Exception {
		var tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
		Files.createDirectories(tmpDir);
		var ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		var db = tmpDir.resolve("packetproxy-" + ts + ".sqlite3");
		Database.getInstance().openAt(db.toString());
		return db.toString();
	}

	public String createNewProject(String name) throws Exception {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Project name cannot be empty");
		}

		var projectDir = Paths.get(System.getProperty("user.home"), ".packetproxy", "projects");
		Files.createDirectories(projectDir);
		var db = projectDir.resolve(name.trim() + ".sqlite3");
		Database.getInstance().openAt(db.toString());
		RecentProjectsStore.add(db);
		return db.toString();
	}
}
