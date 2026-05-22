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
package packetproxy.extensions.securityheaders.exclusion

import java.net.URI
import java.util.UUID

/**
 * Represents an exclusion rule for filtering security header check results. Immutable data class.
 */
data class ExclusionRule(val id: String, val type: ExclusionRuleType, val pattern: String) {
  constructor(
    type: ExclusionRuleType,
    pattern: String,
  ) : this(UUID.randomUUID().toString(), type, pattern)

  init {
    require(id.isNotBlank()) { "id must not be blank" }
    require(pattern.isNotBlank()) { "pattern must not be blank" }
  }

  /**
   * Checks if the given URL matches this exclusion rule.
   *
   * @param method HTTP method (GET, POST, etc.)
   * @param url Full URL (e.g., https://example.com/api/users)
   * @return true if the URL should be excluded
   */
  fun matches(method: String, url: String): Boolean {
    return when (type) {
      ExclusionRuleType.HOST -> matchesHost(url)
      ExclusionRuleType.PATH -> matchesPath(url)
      ExclusionRuleType.ENDPOINT -> matchesEndpoint(method, url)
    }
  }

  private fun matchesHost(url: String): Boolean {
    val host = extractHost(url)
    return host != null && host.equals(pattern, ignoreCase = true)
  }

  private fun matchesPath(url: String): Boolean {
    val path = extractPath(url) ?: return false
    // Exact match or prefix match with wildcard support
    return if (pattern.endsWith("*")) {
      val prefix = pattern.substring(0, pattern.length - 1)
      path.startsWith(prefix)
    } else {
      path == pattern
    }
  }

  private fun matchesEndpoint(method: String, url: String): Boolean {
    val endpoint = "$method $url"
    return endpoint == pattern
  }

  private fun extractHost(url: String): String? {
    return try {
      URI(url).host
    } catch (e: Exception) {
      null
    }
  }

  private fun extractPath(url: String): String? {
    return try {
      val uri = URI(url)
      val path = uri.path
      if (path.isNullOrEmpty()) "/" else path
    } catch (e: Exception) {
      null
    }
  }

  override fun toString(): String = "${type.displayName}: $pattern"
}
