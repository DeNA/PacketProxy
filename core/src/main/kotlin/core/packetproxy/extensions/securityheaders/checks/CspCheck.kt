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

/**
 * Content-Security-Policy (CSP) check. Validates that frame-ancestors directive is properly set to
 * prevent clickjacking.
 */
class CspCheck : SecurityCheck {
  companion object {
    const val CONTEXT_KEY = "csp"
  }

  override val name: String = "Content-Security-Policy"
  override val columnName: String = "CSP"
  override val failMessage: String =
    "Content-Security-Policy with frame-ancestors or X-Frame-Options is missing"

  override val greenPatterns: List<String> =
    listOf("frame-ancestors 'none'", "frame-ancestors 'self'")
  override val redPatterns: List<String> = listOf("content-security-policy:")

  override fun check(header: HttpHeader, context: MutableMap<String, Any>): SecurityCheckResult {
    val csp = header.getValue("Content-Security-Policy").orElse("")
    val xfo = header.getValue("X-Frame-Options").orElse("")

    // Store CSP value in context for other checks that depend on it
    context[CONTEXT_KEY] = csp

    val hasFrameAncestors =
      csp.contains("frame-ancestors 'none'") || csp.contains("frame-ancestors 'self'")

    return when {
      hasFrameAncestors -> {
        if (csp.contains("frame-ancestors 'none'")) {
          SecurityCheckResult.ok("frame-ancestors 'none'", csp)
        } else {
          SecurityCheckResult.ok("frame-ancestors 'self'", csp)
        }
      }
      xfo.isNotEmpty() -> SecurityCheckResult.ok("X-Frame-Options:$xfo", "X-Frame-Options: $xfo")
      csp.isEmpty() -> SecurityCheckResult.fail("(none)", "")
      else -> SecurityCheckResult.fail(csp, csp)
    }
  }

  override fun matchesHeaderLine(headerLine: String): Boolean {
    return headerLine.startsWith("content-security-policy:") ||
      headerLine.startsWith("x-frame-options:")
  }

  override fun getHighlightType(
    headerLine: String,
    result: SecurityCheckResult?,
  ): SecurityCheck.HighlightType {
    // For X-Frame-Options, use the default whole-line highlighting
    val lowerLine = headerLine.lowercase()
    if (lowerLine.startsWith("x-frame-options:")) {
      return super.getHighlightType(headerLine, result)
    }
    // For CSP, we use segment-based highlighting instead
    return SecurityCheck.HighlightType.NONE
  }
}
