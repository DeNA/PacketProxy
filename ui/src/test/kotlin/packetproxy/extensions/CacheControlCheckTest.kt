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
import packetproxy.extensions.securityheaders.checks.CacheControlCheck

class CacheControlCheckTest {
  private lateinit var check: CacheControlCheck
  private lateinit var context: MutableMap<String, Any>

  @BeforeEach
  fun setUp() {
    check = CacheControlCheck()
    context = mutableMapOf()
  }

  // ===== No Cache-Control Header =====

  @Test
  fun testCheck_NoCacheControlNoPragma_Ok() {
    val header = TestHttpHeader.empty()
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("No Cache-Control or Pragma", result.displayValue)
  }

  // ===== Incomplete Cache-Control =====

  @Test
  fun testCheck_OnlyPrivate_Warn() {
    val header = TestHttpHeader.withCacheControl("private")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  @Test
  fun testCheck_OnlyNoStore_Warn() {
    val header = TestHttpHeader.withCacheControl("no-store")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  @Test
  fun testCheck_OnlyNoCache_Warn() {
    val header = TestHttpHeader.withCacheControl("no-cache")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  @Test
  fun testCheck_OnlyMustRevalidate_Warn() {
    val header = TestHttpHeader.withCacheControl("must-revalidate")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  @Test
  fun testCheck_PrivateAndNoStore_Warn() {
    val header = TestHttpHeader.withCacheControl("private, no-store")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  @Test
  fun testCheck_AllDirectivesButNoPragma_Warn() {
    // Has all Cache-Control directives but missing Pragma: no-cache
    val header = TestHttpHeader.withCacheControl("private, no-store, no-cache, must-revalidate")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  // ===== Pragma without Cache-Control =====

  @Test
  fun testCheck_OnlyPragmaNoCache_Warn() {
    val header = TestHttpHeader().addHeader("Pragma", "no-cache").build()
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  // ===== Insecure Cache Configurations =====

  @Test
  fun testCheck_PublicCache_Warn() {
    val header = TestHttpHeader.withCacheControl("public, max-age=3600")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  @Test
  fun testCheck_MaxAgeOnly_Warn() {
    val header = TestHttpHeader.withCacheControl("max-age=86400")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  // ===== Secure Configuration =====

  @Test
  fun testCheck_FullSecureConfig_Ok() {
    val header =
      TestHttpHeader()
        .addHeader("Cache-Control", "private, no-store, no-cache, must-revalidate")
        .addHeader("Pragma", "no-cache")
        .build()
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_FullSecureConfigWithExtraDirectives_Ok() {
    val header =
      TestHttpHeader()
        .addHeader("Cache-Control", "private, no-store, no-cache, must-revalidate, max-age=0")
        .addHeader("Pragma", "no-cache")
        .build()
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Edge Cases =====

  @Test
  fun testCheck_EmptyCacheControl_Ok() {
    val header = TestHttpHeader.withCacheControl("")
    val result = check.check(header, context)

    // Empty string is treated as missing header
    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_WhitespaceOnlyCacheControl_Ok() {
    val header = TestHttpHeader.withCacheControl("   ")
    val result = check.check(header, context)

    // Whitespace-only is treated as missing header
    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_CacheControlWithTypo_Warn() {
    // Misspelled directives
    val header = TestHttpHeader.withCacheControl("privat, no-stor, no-cach, must-revalidat")
    val result = check.check(header, context)

    assertTrue(result.isWarn)
  }

  // ===== matchesHeaderLine =====

  @Test
  fun testMatchesHeaderLine_CacheControl_True() {
    assertTrue(check.matchesHeaderLine("cache-control: no-cache"))
  }

  @Test
  fun testMatchesHeaderLine_OtherHeader_False() {
    assertFalse(check.matchesHeaderLine("pragma: no-cache"))
  }

  @Test
  fun testMatchesHeaderLine_EmptyString_False() {
    assertFalse(check.matchesHeaderLine(""))
  }

  // ===== affectsOverallStatus =====

  @Test
  fun testAffectsOverallStatus_False() {
    assertFalse(check.affectsOverallStatus)
  }

  // ===== Name and Messages =====

  @Test
  fun testGetName() {
    assertEquals("Cache-Control", check.name)
  }

  @Test
  fun testGetColumnName() {
    assertEquals("Cache-Control", check.columnName)
  }

  @Test
  fun testGetFailMessage() {
    assertEquals("Cache-Control is not configured for sensitive data protection", check.failMessage)
  }
}
