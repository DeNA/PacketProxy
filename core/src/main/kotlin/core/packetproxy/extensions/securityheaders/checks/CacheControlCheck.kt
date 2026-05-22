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
package packetproxy.extensions.securityheaders.checks

import packetproxy.extensions.securityheaders.SecurityCheck
import packetproxy.extensions.securityheaders.SecurityCheckResult
import packetproxy.http.HttpHeader

/** Cache-Control check. Validates secure cache configuration for sensitive data. */
class CacheControlCheck : SecurityCheck {
  override val name: String = "Cache-Control"
  override val columnName: String = "Cache-Control"
  override val failMessage: String = "Cache-Control is not configured for sensitive data protection"

  // Cache-Control doesn't affect overall pass/fail
  override val affectsOverallStatus: Boolean = false

  override val yellowPatterns: List<String> = listOf("cache-control:")

  override fun check(header: HttpHeader, context: MutableMap<String, Any>): SecurityCheckResult {
    val cache = header.getValue("Cache-Control").orElse("")
    val pragma = header.getValue("Pragma").orElse("")

    if (cache.isEmpty() && pragma.isEmpty()) {
      return SecurityCheckResult.ok("No Cache-Control or Pragma", "")
    }

    val isSecure =
      cache.contains("private") &&
        cache.contains("no-store") &&
        cache.contains("no-cache") &&
        cache.contains("must-revalidate") &&
        pragma.contains("no-cache")

    if (isSecure) {
      return SecurityCheckResult.ok(cache, cache)
    }
    if (cache.isEmpty()) {
      return SecurityCheckResult.warn("(none)", "(none)")
    }
    return SecurityCheckResult.warn(cache, cache)
  }

  override fun matchesHeaderLine(headerLine: String): Boolean {
    return headerLine.startsWith("cache-control:")
  }
}
