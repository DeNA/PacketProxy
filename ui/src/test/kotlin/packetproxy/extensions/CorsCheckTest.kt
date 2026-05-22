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
import packetproxy.extensions.securityheaders.checks.CorsCheck

class CorsCheckTest {
  private lateinit var check: CorsCheck
  private lateinit var context: MutableMap<String, Any>

  @BeforeEach
  fun setUp() {
    check = CorsCheck()
    context = mutableMapOf()
  }

  // ===== No CORS Header =====

  @Test
  fun testCheck_NoCorsHeader_Ok() {
    val header = TestHttpHeader.empty()
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("No CORS", result.displayValue)
  }

  // ===== Wildcard CORS - Security Issue =====

  @Test
  fun testCheck_WildcardCors_Fail() {
    val header = TestHttpHeader.withCors("*")
    val result = check.check(header, context)

    assertTrue(result.isFail)
    assertEquals("*", result.displayValue)
  }

  // ===== Potentially Dangerous CORS Configurations =====

  @Test
  fun testCheck_WildcardWithSpace_Fail() {
    // " *" is trimmed to "*" by HttpHeader, so it fails
    val header = TestHttpHeader.withCors(" *")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_MultipleWildcards_Ok() {
    // "**" is not exactly "*"
    val header = TestHttpHeader.withCors("**")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_WildcardInUrl_Ok() {
    // Wildcard as part of URL is not the same as just "*"
    val header = TestHttpHeader.withCors("https://*.example.com")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Valid CORS Configurations =====

  @Test
  fun testCheck_SpecificOrigin_Ok() {
    val header = TestHttpHeader.withCors("https://example.com")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("https://example.com", result.displayValue)
  }

  @Test
  fun testCheck_HttpOrigin_Ok() {
    val header = TestHttpHeader.withCors("http://localhost:3000")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_NullOrigin_Ok() {
    // "null" origin is a special case, technically valid
    val header = TestHttpHeader.withCors("null")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Origin Reflection Detection =====

  @Test
  fun testCheck_OriginReflection_DevOrigin_Warn() {
    // Even dev origins should warn if reflected
    val origin = "https://dev.example.com"
    context[CorsCheck.CONTEXT_KEY_REQUEST_ORIGIN] = origin
    val header = TestHttpHeader.withCors(origin)
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  @Test
  fun testCheck_DifferentOrigin_Ok() {
    // When ACAO is different from request Origin, it's OK (static config)
    context[CorsCheck.CONTEXT_KEY_REQUEST_ORIGIN] = "https://a.example.com"
    val header = TestHttpHeader.withCors("https://b.example.com")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("https://b.example.com", result.displayValue)
  }

  @Test
  fun testCheck_NoRequestOrigin_Ok() {
    // When no Origin in request, can't detect reflection
    val header = TestHttpHeader.withCors("https://example.com")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_EmptyRequestOrigin_Ok() {
    // Empty Origin in context should not trigger reflection warning
    context[CorsCheck.CONTEXT_KEY_REQUEST_ORIGIN] = ""
    val header = TestHttpHeader.withCors("https://example.com")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_WildcardStillFails_EvenWithOrigin() {
    // Wildcard should still fail even if Origin is present
    context[CorsCheck.CONTEXT_KEY_REQUEST_ORIGIN] = "https://example.com"
    val header = TestHttpHeader.withCors("*")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== Edge Cases =====

  @Test
  fun testCheck_EmptyCorsValue_Ok() {
    // Empty string is treated as no CORS
    val header = TestHttpHeader.withCors("")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("No CORS", result.displayValue)
  }

  @Test
  fun testCheck_WhitespaceOnlyCors_Ok() {
    // Whitespace is not "*"
    val header = TestHttpHeader.withCors("   ")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_CorsWithPort_Ok() {
    val header = TestHttpHeader.withCors("https://example.com:8443")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_CorsWithPath_Ok() {
    // Technically invalid CORS (should be origin only), but check doesn't validate
    val header = TestHttpHeader.withCors("https://example.com/path")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== matchesHeaderLine =====

  @Test
  fun testMatchesHeaderLine_Cors_True() {
    assertTrue(check.matchesHeaderLine("access-control-allow-origin: https://example.com"))
  }

  @Test
  fun testMatchesHeaderLine_OtherAcHeader_False() {
    assertFalse(check.matchesHeaderLine("access-control-allow-methods: GET, POST"))
  }

  @Test
  fun testMatchesHeaderLine_OtherHeader_False() {
    assertFalse(check.matchesHeaderLine("content-type: application/json"))
  }

  @Test
  fun testMatchesHeaderLine_EmptyString_False() {
    assertFalse(check.matchesHeaderLine(""))
  }

  // ===== Name and Messages =====

  @Test
  fun testGetName() {
    assertEquals("CORS", check.name)
  }

  @Test
  fun testGetColumnName() {
    assertEquals("CORS", check.columnName)
  }

  @Test
  fun testGetFailMessage() {
    assertEquals("CORS wildcard (*) allows all origins", check.failMessage)
  }

  @Test
  fun testGetWarnMessage() {
    assertEquals("Potential CORS Origin reflection vulnerability", check.warnMessage)
  }
}
