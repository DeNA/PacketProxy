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
package packetproxy.model

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionProfilesTest {
  @BeforeEach
  fun setUp() {
    val tempDb = Files.createTempFile("session_profiles_test", ".sqlite3")
    Database.getInstance().openAt(tempDb.toString())
  }

  @Test
  fun queryByName_returnsProfileWhenExists() {
    val profiles = SessionProfiles.getInstance()
    val profile = SessionProfile("userA", "Bearer token-a")
    profiles.create(profile)

    val found = profiles.queryByName("userA")
    assertEquals(profile.id, found?.id)
    assertEquals("Bearer token-a", found?.authorization)
  }

  @Test
  fun queryByName_returnsNullWhenMissing() {
    val profiles = SessionProfiles.getInstance()
    assertNull(profiles.queryByName("missing"))
  }

  @Test
  fun createQueryUpdateDelete() {
    val profiles = SessionProfiles.getInstance()

    val profile = SessionProfile("userA", "Bearer token-a")
    profiles.create(profile)

    val all = profiles.queryAll()
    assertEquals(1, all.size)
    assertEquals("userA", all[0].name)

    val loaded = profiles.query(all[0].id)!!
    loaded.authorization = "Bearer token-a-updated"
    profiles.update(loaded)

    assertEquals("Bearer token-a-updated", profiles.query(loaded.id)?.authorization)

    profiles.delete(loaded)
    assertTrue(profiles.queryAll().isEmpty())
  }

  @Test
  fun formatAuthorizationPreview_truncatesLongValue() {
    val preview = SessionProfile.formatAuthorizationPreview("Bearer very-long-authorization-value")
    assertEquals("Bearer very-long-aut...", preview)
  }
}
