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
import packetproxy.extensions.securityheaders.SecurityCheck.HighlightType
import packetproxy.extensions.securityheaders.checks.CookieCheck

class CookieCheckTest {
  private lateinit var check: CookieCheck
  private lateinit var context: MutableMap<String, Any>

  @BeforeEach
  fun setUp() {
    check = CookieCheck()
    context = mutableMapOf()
  }

  // ===== No Cookie Cases =====

  @Test
  fun testCheck_NoCookies_Ok() {
    val header = TestHttpHeader.empty()
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("No cookies", result.displayValue)
  }

  // ===== Missing Secure Flag =====

  @Test
  fun testCheck_CookieWithoutSecure_Fail() {
    val header = TestHttpHeader.withSetCookie("session=abc123; HttpOnly")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CookieWithHttpOnlyOnly_Fail() {
    val header = TestHttpHeader.withSetCookie("token=xyz; HttpOnly; Path=/")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_SimpleCookieWithoutAttributes_Fail() {
    val header = TestHttpHeader.withSetCookie("name=value")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== Multiple Cookies - Mixed Secure Status =====

  @Test
  fun testCheck_MultipleCookies_OneWithoutSecure_Fail() {
    val header =
      TestHttpHeader()
        .addHeader("Set-Cookie", "cookie1=value1; Secure")
        .addHeader("Set-Cookie", "cookie2=value2; HttpOnly")
        .build()
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_MultipleCookies_AllWithoutSecure_Fail() {
    val header =
      TestHttpHeader()
        .addHeader("Set-Cookie", "cookie1=value1")
        .addHeader("Set-Cookie", "cookie2=value2")
        .addHeader("Set-Cookie", "cookie3=value3")
        .build()
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== Edge Cases with Secure Flag Position =====

  @Test
  fun testCheck_SecureAtBeginning_Fail() {
    // Malformed: "Secure" at beginning, but this is not a valid cookie format
    // RFC 6265 requires first part to be "name=value", so "Secure" is not recognized as an
    // attribute
    val header = TestHttpHeader.withSetCookie("Secure; session=abc123")
    val result = check.check(header, context)

    // "Secure" is not recognized as an attribute (first part must be name=value), so this fails
    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_SecureInValue_Fail() {
    // "secure" appears in cookie value, not as attribute
    val header = TestHttpHeader.withSetCookie("data=this_is_secure_data")
    val result = check.check(header, context)

    assertTrue(result.isFail) // Secure attribute not found
  }

  // ===== False Positive Prevention Tests =====

  @Test
  fun testCheck_CookieNameSecure_WithoutSecureAttribute_Fail() {
    // Cookie name is "Secure" but no Secure attribute
    val header =
      TestHttpHeader.withSetCookie("Secure=Secure%3Dtrue; Path=/; HttpOnly; SameSite=Lax")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CookieNameSecureLowercase_WithoutSecureAttribute_Fail() {
    // Cookie name is "secure" but no Secure attribute
    val header = TestHttpHeader.withSetCookie("secure=Secure%3D1; Path=/; HttpOnly; SameSite=Lax")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CookieNameSecureSession_WithoutSecureAttribute_Fail() {
    // Cookie name contains "Secure" but no Secure attribute
    val header =
      TestHttpHeader.withSetCookie("Secure_session=SECURE_FLAG; Path=/; HttpOnly; SameSite=Lax")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CookieNameSecureId_WithoutSecureAttribute_Fail() {
    // Cookie name contains "Secure" but no Secure attribute
    val header =
      TestHttpHeader.withSetCookie("SecureId=SecureToken; Path=/; HttpOnly; SameSite=Lax")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CookieNameSecureCookie_WithoutSecureAttribute_Fail() {
    // Cookie name contains "Secure" but no Secure attribute
    val header =
      TestHttpHeader.withSetCookie("SecureCookie=secure_value; Path=/; HttpOnly; SameSite=Lax")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_CookieNameSecure_WithSecureAttribute_Ok() {
    // Cookie name is "Secure" AND Secure attribute is present
    val header = TestHttpHeader.withSetCookie("Secure=value; Secure; Path=/")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_CookieNameSecureId_WithSecureAttribute_Ok() {
    // Cookie name contains "Secure" AND Secure attribute is present
    val header = TestHttpHeader.withSetCookie("SecureId=value; Path=/; Secure; HttpOnly")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_SecureFlagWithDifferentCase_Ok() {
    val header = TestHttpHeader.withSetCookie("session=abc123; SECURE")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_SecureFlagMixedCase_Ok() {
    val header = TestHttpHeader.withSetCookie("session=abc123; SeCuRe")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Valid Cookie Cases =====

  @Test
  fun testCheck_CookieWithSecure_Ok() {
    val header = TestHttpHeader.withSetCookie("session=abc123; Secure; HttpOnly")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_MultipleCookies_AllSecure_Ok() {
    val header =
      TestHttpHeader()
        .addHeader("Set-Cookie", "cookie1=value1; Secure")
        .addHeader("Set-Cookie", "cookie2=value2; Secure; HttpOnly")
        .build()
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Context Storage =====

  @Test
  fun testCheck_StoresCookiesInContext() {
    val header =
      TestHttpHeader()
        .addHeader("Set-Cookie", "cookie1=value1; Secure")
        .addHeader("Set-Cookie", "cookie2=value2")
        .build()
    check.check(header, context)

    @Suppress("UNCHECKED_CAST") val cookies = context[CookieCheck.CONTEXT_KEY] as List<String>
    assertNotNull(cookies)
    assertEquals(2, cookies.size)
  }

  @Test
  fun testCheck_NoCookies_StoresEmptyListInContext() {
    val header = TestHttpHeader.empty()
    check.check(header, context)

    @Suppress("UNCHECKED_CAST") val cookies = context[CookieCheck.CONTEXT_KEY] as List<String>
    assertNotNull(cookies)
    assertTrue(cookies.isEmpty())
  }

  // ===== Display Value =====

  @Test
  fun testCheck_LongCookieValue_NotTruncated() {
    val longValue = "a".repeat(100)
    val cookie = "session=$longValue; Secure"
    val header = TestHttpHeader.withSetCookie(cookie)
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertTrue(result.displayValue.contains(cookie))
  }

  // ===== Static hasSecureFlag Method =====

  @Test
  fun testHasSecureFlag_WithSecure_True() {
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: session=abc; secure"))
  }

  @Test
  fun testHasSecureFlag_WithoutSecure_False() {
    assertFalse(CookieCheck.hasSecureFlag("set-cookie: session=abc; httponly"))
  }

  @Test
  fun testHasSecureFlag_EmptyString_False() {
    assertFalse(CookieCheck.hasSecureFlag(""))
  }

  @Test
  fun testHasSecureFlag_SecureInValue_False() {
    // "secure" appears in cookie value, not as attribute - should return false
    assertFalse(CookieCheck.hasSecureFlag("set-cookie: data=secure_value"))
  }

  @Test
  fun testHasSecureFlag_CookieNameSecure_WithoutAttribute_False() {
    // Cookie name is "Secure" but no Secure attribute
    assertFalse(CookieCheck.hasSecureFlag("set-cookie: Secure=value; Path=/"))
  }

  @Test
  fun testHasSecureFlag_CookieNameSecure_WithAttribute_True() {
    // Cookie name is "Secure" AND Secure attribute is present
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: Secure=value; Secure; Path=/"))
  }

  @Test
  fun testHasSecureFlag_CookieNameSecureId_WithoutAttribute_False() {
    // Cookie name contains "Secure" but no Secure attribute
    assertFalse(CookieCheck.hasSecureFlag("set-cookie: SecureId=token; Path=/"))
  }

  @Test
  fun testHasSecureFlag_CookieNameSecureId_WithAttribute_True() {
    // Cookie name contains "Secure" AND Secure attribute is present
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: SecureId=token; Path=/; Secure"))
  }

  @Test
  fun testHasSecureFlag_SecureAttributeWithValue_True() {
    // Secure attribute with value (should still match)
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: session=abc; Secure=true"))
  }

  @Test
  fun testHasSecureFlag_MultipleAttributes_True() {
    // Multiple attributes including Secure
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: session=abc; Path=/; Secure; HttpOnly"))
  }

  @Test
  fun testHasSecureFlag_NoAttributes_False() {
    // Cookie without any attributes
    assertFalse(CookieCheck.hasSecureFlag("set-cookie: session=abc"))
  }

  @Test
  fun testHasSecureFlag_WithoutSetCookiePrefix_True() {
    // Cookie content without "Set-Cookie:" prefix
    assertTrue(CookieCheck.hasSecureFlag("session=abc; Secure"))
  }

  @Test
  fun testHasSecureFlag_CaseInsensitive_True() {
    // Secure attribute in different cases
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: session=abc; SECURE"))
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: session=abc; SeCuRe"))
    assertTrue(CookieCheck.hasSecureFlag("set-cookie: session=abc; secure"))
  }

  // ===== matchesHeaderLine =====

  @Test
  fun testMatchesHeaderLine_SetCookie_True() {
    assertTrue(check.matchesHeaderLine("set-cookie: session=abc"))
  }

  @Test
  fun testMatchesHeaderLine_OtherHeader_False() {
    assertFalse(check.matchesHeaderLine("cookie: session=abc"))
  }

  @Test
  fun testMatchesHeaderLine_EmptyString_False() {
    assertFalse(check.matchesHeaderLine(""))
  }

  // ===== getHighlightSegments =====

  @Test
  fun testGetHighlightSegments_WithSecure_Green() {
    val line = "set-cookie: session=abc123; Secure; HttpOnly"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(0, segments[0].start)
    assertEquals(line.length, segments[0].end)
    assertEquals(HighlightType.GREEN, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_WithoutSecure_Red() {
    val line = "set-cookie: session=abc123; HttpOnly"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(0, segments[0].start)
    assertEquals(line.length, segments[0].end)
    assertEquals(HighlightType.RED, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_NonSetCookieHeader_Empty() {
    val line = "cookie: session=abc123"
    val segments = check.getHighlightSegments(line, null)

    assertTrue(segments.isEmpty())
  }

  @Test
  fun testGetHighlightSegments_SecureUpperCase_Green() {
    val line = "set-cookie: session=abc123; SECURE"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(HighlightType.GREEN, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_SecureInValue_Red() {
    // "secure" in value should be RED (not an attribute)
    val line = "set-cookie: data=this_is_secure_data"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(HighlightType.RED, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_CookieNameSecure_WithoutAttribute_Red() {
    // Cookie name is "Secure" but no Secure attribute
    val line = "set-cookie: Secure=value; Path=/"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(HighlightType.RED, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_CookieNameSecure_WithAttribute_Green() {
    // Cookie name is "Secure" AND Secure attribute is present
    val line = "set-cookie: Secure=value; Secure; Path=/"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(HighlightType.GREEN, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_CookieNameSecureId_WithoutAttribute_Red() {
    // Cookie name contains "Secure" but no Secure attribute
    val line = "set-cookie: SecureId=token; Path=/"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(HighlightType.RED, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_CookieNameSecureId_WithAttribute_Green() {
    // Cookie name contains "Secure" AND Secure attribute is present
    val line = "set-cookie: SecureId=token; Path=/; Secure"
    val segments = check.getHighlightSegments(line, null)

    assertEquals(1, segments.size)
    assertEquals(HighlightType.GREEN, segments[0].type)
  }

  @Test
  fun testGetHighlightSegments_IndependentOfResult() {
    // Highlight is based on Secure flag presence, not on check result
    val lineWithSecure = "set-cookie: session=abc123; Secure"
    val lineWithoutSecure = "set-cookie: session=abc123; HttpOnly"

    // Even with FAIL result, line with Secure should be GREEN
    val failResult = SecurityCheckResult.fail("test", "test")
    val segmentsWithSecure = check.getHighlightSegments(lineWithSecure, failResult)
    assertEquals(HighlightType.GREEN, segmentsWithSecure[0].type)

    // Even with OK result, line without Secure should be RED
    val okResult = SecurityCheckResult.ok("test", "test")
    val segmentsWithoutSecure = check.getHighlightSegments(lineWithoutSecure, okResult)
    assertEquals(HighlightType.RED, segmentsWithoutSecure[0].type)
  }
}
