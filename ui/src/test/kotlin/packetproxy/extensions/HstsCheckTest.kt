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
import packetproxy.extensions.securityheaders.checks.HstsCheck

class HstsCheckTest {
  private lateinit var check: HstsCheck
  private lateinit var context: MutableMap<String, Any>

  @BeforeEach
  fun setUp() {
    check = HstsCheck()
    context = mutableMapOf()
  }

  // ===== Missing Header Cases =====

  @Test
  fun testCheck_NoHstsHeader_Fail() {
    val header = TestHttpHeader.empty()
    val result = check.check(header, context)

    assertTrue(result.isFail)
    assertEquals("(none)", result.displayValue)
  }

  @Test
  fun testCheck_EmptyHstsHeader_Fail() {
    // Empty value is treated as missing
    val header = TestHttpHeader.withHsts("")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_HstsWithWhitespaceOnly_Fail() {
    val header = TestHttpHeader.withHsts("   ")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== Malformed HSTS Values =====

  @Test
  fun testCheck_HstsWithInvalidDirective_Ok() {
    // The check doesn't validate directive format, just presence
    val header = TestHttpHeader.withHsts("invalid-directive")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_HstsWithZeroMaxAge_Ok() {
    // max-age=0 effectively disables HSTS, but check passes
    val header = TestHttpHeader.withHsts("max-age=0")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_HstsWithNegativeMaxAge_Ok() {
    // Invalid but check doesn't validate
    val header = TestHttpHeader.withHsts("max-age=-1")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Valid HSTS Cases =====

  @Test
  fun testCheck_HstsWithMaxAge_Ok() {
    val header = TestHttpHeader.withHsts("max-age=31536000")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertTrue(result.displayValue.contains("max-age=31536000"))
  }

  @Test
  fun testCheck_HstsWithIncludeSubDomains_Ok() {
    val header = TestHttpHeader.withHsts("max-age=31536000; includeSubDomains")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_HstsWithPreload_Ok() {
    val header = TestHttpHeader.withHsts("max-age=31536000; includeSubDomains; preload")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== matchesHeaderLine =====

  @Test
  fun testMatchesHeaderLine_HstsHeader_True() {
    assertTrue(check.matchesHeaderLine("strict-transport-security: max-age=31536000"))
  }

  @Test
  fun testMatchesHeaderLine_OtherHeader_False() {
    assertFalse(check.matchesHeaderLine("content-security-policy: default-src 'self'"))
  }

  @Test
  fun testMatchesHeaderLine_EmptyString_False() {
    assertFalse(check.matchesHeaderLine(""))
  }

  @Test
  fun testMatchesHeaderLine_SimilarHeader_False() {
    assertFalse(check.matchesHeaderLine("x-strict-transport-security: max-age=31536000"))
  }

  // ===== Name and Column =====

  @Test
  fun testGetName() {
    assertEquals("HSTS", check.name)
  }

  @Test
  fun testGetColumnName() {
    assertEquals("HSTS", check.columnName)
  }

  @Test
  fun testGetFailMessage() {
    assertEquals("Strict-Transport-Security header is missing", check.failMessage)
  }
}
