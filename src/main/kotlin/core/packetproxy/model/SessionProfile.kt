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

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable

@DatabaseTable(tableName = "session_profiles")
class SessionProfile {
  @field:DatabaseField(generatedId = true) var id: Int = 0

  @field:DatabaseField(unique = true) var name: String? = null

  @field:DatabaseField var authorization: String? = null

  @field:DatabaseField var cookie: String? = null

  constructor()

  constructor(name: String, authorization: String) {
    this.name = name
    this.authorization = authorization
  }

  companion object {
    @JvmStatic
    fun formatAuthorizationPreview(authorization: String?): String {
      if (authorization.isNullOrEmpty()) {
        return "(none)"
      }
      if (authorization.length <= 20) {
        return authorization
      }
      return authorization.substring(0, 20) + "..."
    }
  }
}
