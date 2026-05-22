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
import packetproxy.extensions.securityheaders.SecurityCheck.HighlightSegment
import packetproxy.extensions.securityheaders.SecurityCheck.HighlightType
import packetproxy.extensions.securityheaders.SecurityCheckResult
import packetproxy.http.HttpHeader

/** Cookie security check (Set-Cookie). Validates that Secure flag is set on all cookies. */
class CookieCheck : SecurityCheck {
  companion object {
    const val CONTEXT_KEY = "cookies"

    /** Check if a specific cookie line has the Secure flag */
    @JvmStatic
    fun hasSecureFlag(cookieLine: String): Boolean {
      val cookieContent = cookieLine.substringAfter(":", cookieLine).trim()

      if (cookieContent.isEmpty()) {
        return false
      }

      val parts = cookieContent.split(";")

      return parts
        .drop(1)
        .map { it.trim().lowercase() }
        .any { attr ->
          val attrName = attr.split("=").first().trim()
          attrName == "secure"
        }
    }
  }

  override val name: String = "Cookies"
  override val columnName: String = "Cookies"
  override val failMessage: String = "Set-Cookie is missing 'Secure' flag"

  override fun check(header: HttpHeader, context: MutableMap<String, Any>): SecurityCheckResult {
    val setCookies = header.getAllValue("Set-Cookie")

    // Store cookies in context for detailed display
    context[CONTEXT_KEY] = setCookies

    if (setCookies.isEmpty()) {
      return SecurityCheckResult.ok("No cookies", "")
    }

    var allSecure = true
    val displayBuilder = StringBuilder()

    for (cookie in setCookies) {
      if (!hasSecureFlag(cookie)) {
        allSecure = false
      }

      displayBuilder.append(cookie).append("; ")
    }

    val displayValue = displayBuilder.toString()
    val rawValue = setCookies.joinToString("; ")

    return if (allSecure) {
      SecurityCheckResult.ok(displayValue, rawValue)
    } else {
      SecurityCheckResult.fail(displayValue, rawValue)
    }
  }

  override fun matchesHeaderLine(headerLine: String): Boolean {
    return headerLine.startsWith("set-cookie:")
  }

  /**
   * Highlight each Set-Cookie line based on whether it has the Secure flag.
   * - Secure flag present: GREEN (secure)
   * - Secure flag missing: RED (insecure)
   */
  override fun getHighlightSegments(
    headerLine: String,
    result: SecurityCheckResult?,
  ): List<HighlightSegment> {
    if (!matchesHeaderLine(headerLine.lowercase())) {
      return emptyList()
    }

    val hasSecure = hasSecureFlag(headerLine)
    val highlightType = if (hasSecure) HighlightType.GREEN else HighlightType.RED

    return listOf(HighlightSegment(0, headerLine.length, highlightType))
  }
}
