/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.extensions.securityheaders

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import packetproxy.extensions.securityheaders.checks.CspCheck
import packetproxy.extensions.securityheaders.checks.XssProtectionCheck

class XssProtectionCheckTest {
  private lateinit var check: XssProtectionCheck
  private lateinit var context: MutableMap<String, Any>

  @BeforeEach
  fun setUp() {
    check = XssProtectionCheck()
    context = mutableMapOf()
  }

  // ===== No Protection at All =====

  @Test
  fun testCheck_NoXContentTypeOptionsNoCsp_Fail() {
    val header = TestHttpHeader.empty()
    // Ensure CSP is not in context
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    assertTrue(result.isFail)
    assertEquals("(none)", result.displayValue)
  }

  @Test
  fun testCheck_NoCspInContext_Fail() {
    val header = TestHttpHeader.empty()
    // No CSP key in context
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== Wrong X-Content-Type-Options Value =====

  @Test
  fun testCheck_XContentTypeOptionsWrongValue_Fail() {
    val header = TestHttpHeader.withXContentTypeOptions("sniff")
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_XContentTypeOptionsEmptyValue_Fail() {
    val header = TestHttpHeader.withXContentTypeOptions("")
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_XContentTypeOptionsPartialMatch_Fail() {
    // "nosniff-extra" is not "nosniff"
    val header = TestHttpHeader.withXContentTypeOptions("nosniff-extra")
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_XContentTypeOptionsWithSpace_Ok() {
    // "nosniff " with trailing space - trimmed by HttpHeader
    val header = TestHttpHeader.withXContentTypeOptions("nosniff ")
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    // HttpHeader trims values, so this passes
    assertTrue(result.isOk)
  }

  // ===== X-Content-Type-Options: nosniff =====

  @Test
  fun testCheck_NosniffLowercase_Ok() {
    val header = TestHttpHeader.withXContentTypeOptions("nosniff")
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("nosniff", result.displayValue)
  }

  @Test
  fun testCheck_NosniffUppercase_Ok() {
    val header = TestHttpHeader.withXContentTypeOptions("NOSNIFF")
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_NosniffMixedCase_Ok() {
    val header = TestHttpHeader.withXContentTypeOptions("NoSnIfF")
    context[CspCheck.CONTEXT_KEY] = ""
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== CSP as Alternative Protection =====

  @Test
  fun testCheck_BothNosniffAndCsp_Ok() {
    val header = TestHttpHeader.withXContentTypeOptions("nosniff")
    context[CspCheck.CONTEXT_KEY] = "default-src 'self'"
    val result = check.check(header, context)

    assertTrue(result.isOk)
    // Should prefer showing nosniff over CSP
    assertEquals("nosniff", result.displayValue)
  }

  // ===== matchesHeaderLine =====

  @Test
  fun testMatchesHeaderLine_XContentTypeOptions_True() {
    assertTrue(check.matchesHeaderLine("x-content-type-options: nosniff"))
  }

  @Test
  fun testMatchesHeaderLine_OtherHeader_False() {
    assertFalse(check.matchesHeaderLine("content-type: text/html"))
  }

  @Test
  fun testMatchesHeaderLine_XxssProtection_False() {
    // This check is for X-Content-Type-Options, not X-XSS-Protection
    assertFalse(check.matchesHeaderLine("x-xss-protection: 1; mode=block"))
  }

  @Test
  fun testMatchesHeaderLine_EmptyString_False() {
    assertFalse(check.matchesHeaderLine(""))
  }

  // ===== Name and Messages =====

  @Test
  fun testGetName() {
    assertEquals("XSS Protection", check.name)
  }

  @Test
  fun testGetColumnName() {
    assertEquals("XSS Protection", check.columnName)
  }

  @Test
  fun testGetFailMessage() {
    assertEquals("X-Content-Type-Options: nosniff is missing", check.failMessage)
  }
}
