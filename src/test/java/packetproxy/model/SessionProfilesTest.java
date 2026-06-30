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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SessionProfilesTest {

	@BeforeEach
	public void setUp() throws Exception {
		var tempDb = Files.createTempFile("session_profiles_test", ".sqlite3");
		Database.getInstance().openAt(tempDb.toString());
	}

	@Test
	public void queryByName_returnsProfileWhenExists() throws Exception {
		var profiles = SessionProfiles.getInstance();
		var profile = new SessionProfile("userA", "Bearer token-a");
		profiles.create(profile);

		var found = profiles.queryByName("userA");
		assertEquals(profile.getId(), found.getId());
		assertEquals("Bearer token-a", found.getAuthorization());
	}

	@Test
	public void queryByName_returnsNullWhenMissing() throws Exception {
		var profiles = SessionProfiles.getInstance();
		assertNull(profiles.queryByName("missing"));
	}

	@Test
	public void createQueryUpdateDelete() throws Exception {
		var profiles = SessionProfiles.getInstance();

		var profile = new SessionProfile("userA", "Bearer token-a");
		profiles.create(profile);

		var all = profiles.queryAll();
		assertEquals(1, all.size());
		assertEquals("userA", all.get(0).getName());

		var loaded = profiles.query(all.get(0).getId());
		loaded.setAuthorization("Bearer token-a-updated");
		profiles.update(loaded);

		assertEquals("Bearer token-a-updated", profiles.query(loaded.getId()).getAuthorization());

		profiles.delete(loaded);
		assertTrue(profiles.queryAll().isEmpty());
	}

	@Test
	public void formatAuthorizationPreview_truncatesLongValue() {
		var preview = SessionProfile.formatAuthorizationPreview("Bearer very-long-authorization-value");
		assertEquals("Bearer very-long-aut...", preview);
	}
}
