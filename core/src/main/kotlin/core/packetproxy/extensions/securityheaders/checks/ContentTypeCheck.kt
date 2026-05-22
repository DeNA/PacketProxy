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

/** Content-Type check. Validates that charset is specified for text/html responses. */
class ContentTypeCheck : SecurityCheck {
  override val name: String = "Content-Type"
  override val columnName: String = "Content-Type"
  override val failMessage: String = "Content-Type header is missing charset for text/html"

  override fun check(header: HttpHeader, context: MutableMap<String, Any>): SecurityCheckResult {
    val contentType = header.getValue("Content-Type").orElse("")
    val lowerContentType = contentType.lowercase()

    // For non-HTML content, just return OK
    if (!lowerContentType.contains("text/html")) {
      return SecurityCheckResult.ok(contentType, contentType)
    }

    // Only check charset for text/html
    return if (lowerContentType.contains("charset=")) {
      SecurityCheckResult.ok(contentType, contentType)
    } else {
      SecurityCheckResult.fail("No charset", contentType)
    }
  }

  override fun matchesHeaderLine(headerLine: String): Boolean {
    return headerLine.startsWith("content-type:")
  }
}
