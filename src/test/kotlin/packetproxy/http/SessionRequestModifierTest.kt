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
package packetproxy.http

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import packetproxy.model.SessionProfile

class SessionRequestModifierTest {
  @Test
  fun apply_nullProfile_returnsOriginalBytes() {
    val original = sampleRequest("Bearer original")

    val result = SessionRequestModifier.apply(original, null)

    assertArrayEquals(original, result)
  }

  @Test
  fun apply_profileReplacesAuthorization() {
    val original = sampleRequest("Bearer original")
    val profile = SessionProfile("userB", "Bearer userB-token")

    val result = SessionRequestModifier.apply(original, profile)
    val http = Http.create(result)

    assertEquals("Bearer userB-token", http.getFirstHeader("Authorization"))
  }

  @Test
  fun apply_emptyAuthorizationRemovesHeader() {
    val original = sampleRequest("Bearer original")
    val profile = SessionProfile("anonymous", "")

    val result = SessionRequestModifier.apply(original, profile)
    val http = Http.create(result)

    assertFalse(http.getHeader().getValue("Authorization").isPresent)
  }

  @Test
  fun apply_responsePacket_returnsOriginalBytes() {
    val response =
      ("HTTP/1.1 200 OK\r\n" +
          "Content-Type: text/plain\r\n" +
          "Content-Length: 2\r\n" +
          "\r\n" +
          "OK")
        .toByteArray()
    val profile = SessionProfile("userB", "Bearer userB-token")

    val result = SessionRequestModifier.apply(response, profile)

    assertArrayEquals(response, result)
  }

  @Test
  fun apply_invalidBytes_returnsOriginalBytes() {
    val invalid = byteArrayOf(0x00, 0x01, 0x02)
    val profile = SessionProfile("userB", "Bearer userB-token")

    val result = SessionRequestModifier.apply(invalid, profile)

    assertArrayEquals(invalid, result)
  }

  private fun sampleRequest(authorization: String): ByteArray {
    return ("GET /api/users HTTP/1.1\r\n" +
        "Host: example.com\r\n" +
        "Authorization: $authorization\r\n" +
        "Content-Length: 0\r\n" +
        "\r\n")
      .toByteArray()
  }
}
