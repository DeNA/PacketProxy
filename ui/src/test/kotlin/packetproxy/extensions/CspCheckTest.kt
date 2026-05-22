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

class CspCheckTest {
  private lateinit var check: CspCheck
  private lateinit var context: MutableMap<String, Any>

  @BeforeEach
  fun setUp() {
    check = CspCheck()
    context = mutableMapOf()
  }

  // ===== Missing Header Cases =====

  @Test
  fun testCheck_NoCspNoXfo_Fail() {
    val header = TestHttpHeader.empty()
    val result = check.check(header, context)

    assertTrue(result.isFail)
    assertEquals("(none)", result.displayValue)
  }

  // ===== CSP without frame-ancestors =====

  @Test
  fun testCheck_CspWithoutFrameAncestors_Fail() {
    val header = TestHttpHeader.withCsp("default-src 'self'")
    val result = check.check(header, context)

    assertTrue(result.isFail)
    assertEquals("default-src 'self'", result.displayValue)
  }

  @Test
  fun testCheck_CspWithOnlyDefaultSrc_Fail() {
    val header = TestHttpHeader.withCsp("default-src https:")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CspWithScriptSrcOnly_Fail() {
    val header = TestHttpHeader.withCsp("script-src 'self' 'unsafe-inline'")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== Malformed CSP Values =====

  @Test
  fun testCheck_CspWithEmptyValue_Fail() {
    val header = TestHttpHeader.withCsp("")
    val result = check.check(header, context)

    assertTrue(result.isFail)
    assertEquals("(none)", result.displayValue)
  }

  @Test
  fun testCheck_CspWithWhitespaceOnly_Fail() {
    val header = TestHttpHeader.withCsp("   ")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CspWithPartialFrameAncestors_Fail() {
    // "frame-ancestors" without proper value
    val header = TestHttpHeader.withCsp("frame-ancestors")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CspWithMisspelledFrameAncestors_Fail() {
    val header = TestHttpHeader.withCsp("frame-ancestor 'self'")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CspWithFrameAncestorsWrongQuotes_Fail() {
    // Double quotes instead of single quotes
    val header = TestHttpHeader.withCsp("frame-ancestors \"self\"")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== X-Frame-Options Fallback =====

  @Test
  fun testCheck_XfoOnly_Ok() {
    val header = TestHttpHeader.withXFrameOptions("DENY")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("X-Frame-Options:DENY", result.displayValue)
  }

  @Test
  fun testCheck_XfoSameorigin_Ok() {
    val header = TestHttpHeader.withXFrameOptions("SAMEORIGIN")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Valid CSP Cases =====

  @Test
  fun testCheck_CspWithFrameAncestorsNone_Ok() {
    val header = TestHttpHeader.withCsp("frame-ancestors 'none'")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("frame-ancestors 'none'", result.displayValue)
  }

  @Test
  fun testCheck_CspWithFrameAncestorsSelf_Ok() {
    val header = TestHttpHeader.withCsp("frame-ancestors 'self'")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("frame-ancestors 'self'", result.displayValue)
  }

  // ===== Context Storage =====

  @Test
  fun testCheck_StoresCspInContext() {
    val cspValue = "default-src 'self'; frame-ancestors 'none'"
    val header = TestHttpHeader.withCsp(cspValue)
    check.check(header, context)

    assertEquals(cspValue, context[CspCheck.CONTEXT_KEY])
  }

  @Test
  fun testCheck_EmptyCsp_StoresEmptyInContext() {
    val header = TestHttpHeader.empty()
    check.check(header, context)

    assertEquals("", context[CspCheck.CONTEXT_KEY])
  }

  // ===== matchesHeaderLine =====

  @Test
  fun testMatchesHeaderLine_CspHeader_True() {
    assertTrue(check.matchesHeaderLine("content-security-policy: default-src 'self'"))
  }

  @Test
  fun testMatchesHeaderLine_XfoHeader_True() {
    assertTrue(check.matchesHeaderLine("x-frame-options: DENY"))
  }

  @Test
  fun testMatchesHeaderLine_OtherHeader_False() {
    assertFalse(check.matchesHeaderLine("content-type: text/html"))
  }

  @Test
  fun testMatchesHeaderLine_EmptyString_False() {
    assertFalse(check.matchesHeaderLine(""))
  }

  @Test
  fun testMatchesHeaderLine_PartialMatch_False() {
    assertFalse(check.matchesHeaderLine("x-content-security-policy: default-src 'self'"))
  }

  // ===== Green Patterns =====

  @Test
  fun testGetGreenPatterns_ContainsFrameAncestorsNone() {
    assertTrue(check.greenPatterns.contains("frame-ancestors 'none'"))
  }

  @Test
  fun testGetGreenPatterns_ContainsFrameAncestorsSelf() {
    assertTrue(check.greenPatterns.contains("frame-ancestors 'self'"))
  }
}
