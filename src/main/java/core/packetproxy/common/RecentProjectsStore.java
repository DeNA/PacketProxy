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
package packetproxy.common;

import static packetproxy.util.Logging.errWithStackTrace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class RecentProjectsStore {

	private static final int MAX_RECENTS = 10;
	private static final Path RECENT_FILE = Paths.get(System.getProperty("user.home"), ".packetproxy",
			"recent_projects");

	public static List<String> load() {
		try {

			if (!Files.exists(RECENT_FILE)) {

				return new ArrayList<>();
			}
			return Files.readAllLines(RECENT_FILE, StandardCharsets.UTF_8).stream().map(String::trim)
					.filter(s -> !s.isEmpty()).collect(Collectors.toList());
		} catch (Exception e) {

			errWithStackTrace(e);
			return new ArrayList<>();
		}
	}

	public static void add(Path path) {
		try {

			var recents = load();
			var dedup = new LinkedHashSet<String>();
			dedup.add(path.toString());
			dedup.addAll(recents);
			var merged = new ArrayList<>(dedup);
			if (merged.size() > MAX_RECENTS) {

				merged = new ArrayList<>(merged.subList(0, MAX_RECENTS));
			}
			save(merged);
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	public static void save(List<String> recents) throws Exception {
		var dir = RECENT_FILE.getParent();
		if (dir != null && !Files.exists(dir)) {

			Files.createDirectories(dir);
		}
		Files.write(RECENT_FILE, recents, StandardCharsets.UTF_8);
	}
}
