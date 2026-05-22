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
import org.junit.jupiter.api.Test
import packetproxy.extensions.securityheaders.SecurityCheck.HighlightSegment
import packetproxy.extensions.securityheaders.SecurityCheck.HighlightType
import packetproxy.http.HttpHeader

class HighlightSegmentTest {
  // ===== HighlightSegment Construction =====

  @Test
  fun testHighlightSegment_BasicConstruction() {
    val segment = HighlightSegment(0, 10, HighlightType.GREEN)
    assertEquals(0, segment.start)
    assertEquals(10, segment.end)
    assertEquals(HighlightType.GREEN, segment.type)
  }

  @Test
  fun testHighlightSegment_ZeroLength() {
    val segment = HighlightSegment(5, 5, HighlightType.RED)
    assertEquals(5, segment.start)
    assertEquals(5, segment.end)
  }

  @Test
  fun testHighlightSegment_NegativeIndices() {
    // Constructor doesn't validate, so negative values are allowed
    val segment = HighlightSegment(-1, -1, HighlightType.YELLOW)
    assertEquals(-1, segment.start)
    assertEquals(-1, segment.end)
  }

  @Test
  fun testHighlightSegment_StartGreaterThanEnd() {
    // Constructor doesn't validate order
    val segment = HighlightSegment(10, 5, HighlightType.RED)
    assertEquals(10, segment.start)
    assertEquals(5, segment.end)
  }

  // ===== HighlightType Values =====

  @Test
  fun testHighlightType_AllValues() {
    assertEquals(4, HighlightType.values().size)
    assertNotNull(HighlightType.GREEN)
    assertNotNull(HighlightType.RED)
    assertNotNull(HighlightType.YELLOW)
    assertNotNull(HighlightType.NONE)
  }

  // ===== getHighlightType Default Implementation =====

  @Test
  fun testGetHighlightType_NullResult_ReturnsNone() {
    val testCheck = createTestCheck("test-header:", emptyList())
    val type = testCheck.getHighlightType("test-header: value", null)
    assertEquals(HighlightType.NONE, type)
  }

  @Test
  fun testGetHighlightType_NonMatchingHeader_ReturnsNone() {
    val testCheck = createTestCheck("test-header:", emptyList())
    val result = SecurityCheckResult.ok("ok", "ok")
    val type = testCheck.getHighlightType("other-header: value", result)
    assertEquals(HighlightType.NONE, type)
  }

  @Test
  fun testGetHighlightType_OkResult_ReturnsGreen() {
    val testCheck = createTestCheck("test-header:", emptyList())
    val result = SecurityCheckResult.ok("ok", "ok")
    val type = testCheck.getHighlightType("test-header: value", result)
    assertEquals(HighlightType.GREEN, type)
  }

  @Test
  fun testGetHighlightType_FailResult_ReturnsRed() {
    val testCheck = createTestCheck("test-header:", emptyList())
    val result = SecurityCheckResult.fail("fail", "fail")
    val type = testCheck.getHighlightType("test-header: value", result)
    assertEquals(HighlightType.RED, type)
  }

  @Test
  fun testGetHighlightType_WarnResult_ReturnsYellow() {
    val testCheck = createTestCheck("test-header:", emptyList())
    val result = SecurityCheckResult.warn("warn", "warn")
    val type = testCheck.getHighlightType("test-header: value", result)
    assertEquals(HighlightType.YELLOW, type)
  }

  // ===== getHighlightSegments Default Implementation =====

  @Test
  fun testGetHighlightSegments_NonMatchingHeader_ReturnsEmpty() {
    val testCheck = createTestCheck("test-header:", emptyList())
    val result = SecurityCheckResult.ok("ok", "ok")
    val segments = testCheck.getHighlightSegments("other-header: value", result)
    assertTrue(segments.isEmpty())
  }

  @Test
  fun testGetHighlightSegments_NoPatterns_ReturnsEmpty() {
    val testCheck = createTestCheck("test-header:", emptyList())
    val result = SecurityCheckResult.ok("ok", "ok")
    val segments = testCheck.getHighlightSegments("test-header: value", result)
    assertTrue(segments.isEmpty())
  }

  @Test
  fun testGetHighlightSegments_WithGreenPattern_ReturnsSegments() {
    val testCheck =
      createTestCheckWithPatterns("test-header:", emptyList(), emptyList(), listOf("safe"))
    val result = SecurityCheckResult.ok("ok", "ok")
    val segments = testCheck.getHighlightSegments("test-header: safe", result)
    assertFalse(segments.isEmpty())
  }

  @Test
  fun testGetHighlightSegments_EmptyLine_ReturnsEmpty() {
    val testCheck = createTestCheck("", listOf("pattern"))
    val result = SecurityCheckResult.ok("ok", "ok")
    val segments = testCheck.getHighlightSegments("", result)
    assertTrue(segments.isEmpty())
  }

  // ===== Pattern Priority Tests =====

  @Test
  fun testGetHighlightSegments_GreenOverridesRed() {
    val testCheck =
      createTestCheckWithPatterns(
        "test:",
        listOf("value"), // red
        emptyList(), // yellow
        listOf("value"),
      ) // green (higher priority)
    val result = SecurityCheckResult.ok("ok", "ok")
    val segments = testCheck.getHighlightSegments("test: value", result)

    // Green should win due to higher priority
    val hasGreen = segments.any { it.type == HighlightType.GREEN }
    assertTrue(hasGreen)
  }

  // ===== affectsOverallStatus Default =====

  @Test
  fun testAffectsOverallStatus_DefaultTrue() {
    val testCheck = createTestCheck("test:", emptyList())
    assertTrue(testCheck.affectsOverallStatus)
  }

  // ===== Helper Methods =====

  private fun createTestCheck(headerPrefix: String, redPatterns: List<String>): SecurityCheck {
    return createTestCheckWithPatterns(headerPrefix, redPatterns, emptyList(), emptyList())
  }

  private fun createTestCheckWithPatterns(
    headerPrefix: String,
    redPatterns: List<String>,
    yellowPatterns: List<String>,
    greenPatterns: List<String>,
  ): SecurityCheck {
    return object : SecurityCheck {
      override val name: String = "Test"
      override val columnName: String = "Test"
      override val failMessage: String = "Test missing"

      override fun check(
        header: HttpHeader,
        context: MutableMap<String, Any>,
      ): SecurityCheckResult {
        return SecurityCheckResult.ok("ok", "ok")
      }

      override fun matchesHeaderLine(headerLine: String): Boolean {
        return headerLine.lowercase().startsWith(headerPrefix.lowercase())
      }

      override val redPatterns: List<String> = redPatterns
      override val yellowPatterns: List<String> = yellowPatterns
      override val greenPatterns: List<String> = greenPatterns
    }
  }
}
