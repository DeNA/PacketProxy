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

/** HSTS (Strict-Transport-Security) check. Validates that HSTS header is present. */
class HstsCheck : SecurityCheck {
  override val name: String = "HSTS"
  override val columnName: String = "HSTS"
  override val failMessage: String = "Strict-Transport-Security header is missing"

  override fun check(header: HttpHeader, context: MutableMap<String, Any>): SecurityCheckResult {
    val hsts = header.getValue("Strict-Transport-Security").orElse("")

    return if (hsts.isNotEmpty()) {
      SecurityCheckResult.ok("Strict-Transport-Security: $hsts", hsts)
    } else {
      SecurityCheckResult.fail("(none)", "")
    }
  }

  override fun matchesHeaderLine(headerLine: String): Boolean {
    return headerLine.startsWith("strict-transport-security:")
  }
}
