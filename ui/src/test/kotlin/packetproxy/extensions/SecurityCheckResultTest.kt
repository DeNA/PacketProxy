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

class SecurityCheckResultTest {
  // ===== Constructor - Abnormal Cases =====

  @Test
  fun testConstructor_NullDisplayValue_SetsDefault() {
    val result = SecurityCheckResult(SecurityCheckResult.Status.OK, null, "raw")
    assertEquals("OK", result.displayValue)
  }

  @Test
  fun testConstructor_NullRawValue_SetsEmptyString() {
    val result = SecurityCheckResult(SecurityCheckResult.Status.FAIL, "display", null)
    assertEquals("", result.rawValue)
  }

  @Test
  fun testConstructor_AllNullExceptStatus_SetsDefaults() {
    val result = SecurityCheckResult(SecurityCheckResult.Status.FAIL, null, null)
    assertEquals("FAIL", result.displayValue)
    assertEquals("", result.rawValue)
  }

  @Test
  fun testConstructor_WarnStatus_NullDisplay_SetsWarnDefault() {
    val result = SecurityCheckResult(SecurityCheckResult.Status.WARN, null, null)
    assertEquals("WARN", result.displayValue)
  }

  // ===== Static Factory Methods - Edge Cases =====

  @Test
  fun testOk_EmptyStrings() {
    val result = SecurityCheckResult.ok("", "")
    assertTrue(result.isOk)
    assertEquals("", result.displayValue)
    assertEquals("", result.rawValue)
  }

  @Test
  fun testFail_EmptyStrings() {
    val result = SecurityCheckResult.fail("", "")
    assertTrue(result.isFail)
    assertEquals("", result.displayValue)
  }

  @Test
  fun testWarn_EmptyStrings() {
    val result = SecurityCheckResult.warn("", "")
    assertTrue(result.isWarn)
  }

  @Test
  fun testOk_NullValues() {
    val result = SecurityCheckResult.ok(null, null)
    assertTrue(result.isOk)
    assertEquals("OK", result.displayValue)
    assertEquals("", result.rawValue)
  }

  @Test
  fun testFail_NullValues() {
    val result = SecurityCheckResult.fail(null, null)
    assertTrue(result.isFail)
    assertEquals("FAIL", result.displayValue)
  }

  @Test
  fun testWarn_NullValues() {
    val result = SecurityCheckResult.warn(null, null)
    assertTrue(result.isWarn)
    assertEquals("WARN", result.displayValue)
  }

  // ===== Status Methods - Mutual Exclusivity =====

  @Test
  fun testStatusMutualExclusivity_Ok() {
    val result = SecurityCheckResult.ok("test", "test")
    assertTrue(result.isOk)
    assertFalse(result.isFail)
    assertFalse(result.isWarn)
  }

  @Test
  fun testStatusMutualExclusivity_Fail() {
    val result = SecurityCheckResult.fail("test", "test")
    assertFalse(result.isOk)
    assertTrue(result.isFail)
    assertFalse(result.isWarn)
  }

  @Test
  fun testStatusMutualExclusivity_Warn() {
    val result = SecurityCheckResult.warn("test", "test")
    assertFalse(result.isOk)
    assertFalse(result.isFail)
    assertTrue(result.isWarn)
  }

  // ===== Edge Cases with Special Characters =====

  @Test
  fun testConstructor_SpecialCharactersInValues() {
    val specialChars = "テスト<script>alert('xss')</script>\n\r\t"
    val result = SecurityCheckResult.ok(specialChars, specialChars)
    assertEquals(specialChars, result.displayValue)
    assertEquals(specialChars, result.rawValue)
  }

  @Test
  fun testConstructor_VeryLongStrings() {
    val longString = "a".repeat(10000)
    val result = SecurityCheckResult.ok(longString, longString)
    assertEquals(longString, result.displayValue)
    assertEquals(longString, result.rawValue)
  }

  @Test
  fun testConstructor_WhitespaceOnlyStrings() {
    val result = SecurityCheckResult.ok("   ", "\t\n\r")
    assertEquals("   ", result.displayValue)
    assertEquals("\t\n\r", result.rawValue)
  }
}
