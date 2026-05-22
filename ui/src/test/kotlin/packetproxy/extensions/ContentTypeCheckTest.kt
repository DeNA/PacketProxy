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
import packetproxy.extensions.securityheaders.checks.ContentTypeCheck

class ContentTypeCheckTest {
  private lateinit var check: ContentTypeCheck
  private lateinit var context: MutableMap<String, Any>

  @BeforeEach
  fun setUp() {
    check = ContentTypeCheck()
    context = mutableMapOf()
  }

  // ===== Missing Header Cases =====

  @Test
  fun testCheck_NoContentTypeHeader_Ok() {
    // Missing header is treated as OK (non-HTML)
    val header = TestHttpHeader.empty()
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== text/html without charset =====

  @Test
  fun testCheck_TextHtmlWithoutCharset_Fail() {
    val header = TestHttpHeader.withContentType("text/html")
    val result = check.check(header, context)

    assertTrue(result.isFail)
    assertEquals("No charset", result.displayValue)
  }

  @Test
  fun testCheck_TextHtmlUppercaseWithoutCharset_Fail() {
    val header = TestHttpHeader.withContentType("TEXT/HTML")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_TextHtmlMixedCaseWithoutCharset_Fail() {
    val header = TestHttpHeader.withContentType("Text/Html")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_TextHtmlWithExtraParams_NoCharset_Fail() {
    val header = TestHttpHeader.withContentType("text/html; boundary=something")
    val result = check.check(header, context)

    assertTrue(result.isFail)
  }

  // ===== Malformed charset =====

  @Test
  fun testCheck_TextHtmlWithEmptyCharset_Ok() {
    // "charset=" is present, even if empty
    val header = TestHttpHeader.withContentType("text/html; charset=")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_TextHtmlWithCharsetInValue_Ok() {
    // Edge case: charset appears somewhere in value
    val header = TestHttpHeader.withContentType("text/html; custom-charset=utf-8")
    val result = check.check(header, context)

    // This passes because it contains "charset="
    assertTrue(result.isOk)
  }

  // ===== Non-HTML Content Types =====

  @Test
  fun testCheck_ApplicationJson_Ok() {
    val header = TestHttpHeader.withContentType("application/json")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("application/json", result.displayValue)
  }

  @Test
  fun testCheck_TextPlain_Ok() {
    val header = TestHttpHeader.withContentType("text/plain")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_ImagePng_Ok() {
    val header = TestHttpHeader.withContentType("image/png")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_ApplicationXml_Ok() {
    val header = TestHttpHeader.withContentType("application/xml")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Valid text/html with charset =====

  @Test
  fun testCheck_TextHtmlWithCharsetUtf8_Ok() {
    val header = TestHttpHeader.withContentType("text/html; charset=utf-8")
    val result = check.check(header, context)

    assertTrue(result.isOk)
    assertEquals("text/html; charset=utf-8", result.displayValue)
  }

  @Test
  fun testCheck_TextHtmlWithCharsetIso_Ok() {
    val header = TestHttpHeader.withContentType("text/html; charset=ISO-8859-1")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_TextHtmlWithMultipleParams_Ok() {
    val header = TestHttpHeader.withContentType("text/html; charset=utf-8; boundary=something")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== Edge Cases =====

  @Test
  fun testCheck_EmptyContentType_Ok() {
    val header = TestHttpHeader.withContentType("")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_WhitespaceOnlyContentType_Ok() {
    val header = TestHttpHeader.withContentType("   ")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  @Test
  fun testCheck_TextHtmlxWithoutCharset_Fail() {
    // "text/htmlx" contains "text/html" substring, so charset is required
    val header = TestHttpHeader.withContentType("text/htmlx")
    val result = check.check(header, context)

    // Implementation uses contains() so this matches text/html
    assertTrue(result.isFail)
  }

  @Test
  fun testCheck_ApplicationXhtmlXml_Ok() {
    // XHTML is not exactly "text/html"
    val header = TestHttpHeader.withContentType("application/xhtml+xml")
    val result = check.check(header, context)

    assertTrue(result.isOk)
  }

  // ===== matchesHeaderLine =====

  @Test
  fun testMatchesHeaderLine_ContentType_True() {
    assertTrue(check.matchesHeaderLine("content-type: text/html"))
  }

  @Test
  fun testMatchesHeaderLine_OtherHeader_False() {
    assertFalse(check.matchesHeaderLine("cache-control: no-cache"))
  }

  @Test
  fun testMatchesHeaderLine_EmptyString_False() {
    assertFalse(check.matchesHeaderLine(""))
  }

  // ===== Name and Messages =====

  @Test
  fun testGetName() {
    assertEquals("Content-Type", check.name)
  }

  @Test
  fun testGetColumnName() {
    assertEquals("Content-Type", check.columnName)
  }

  @Test
  fun testGetFailMessage() {
    assertEquals("Content-Type header is missing charset for text/html", check.failMessage)
  }
}
