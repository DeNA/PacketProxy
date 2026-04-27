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
package packetproxy.grpc

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrpcProtoWireFormatTest {
  private fun resource(classpathPath: String): File {
    val u =
      GrpcProtoWireFormatTest::class.java.getResource(classpathPath)
        ?: throw IllegalStateException("missing resource: $classpathPath")
    return File(u.toURI())
  }

  private fun wireFormat(): GrpcProtoWireFormat =
    GrpcProtoWireFormat.create(resource("proto/testsvc.desc"))

  @Test
  fun decodeThenEncode_request_roundtrip() {
    val wireFormat = wireFormat()
    val grpcPath = "/pp.testsvc.Greeter/SayHello"
    val json = "{\n  \"name\": \"Alice\"\n}"
    val encodedOnce =
      wireFormat.encodeRequestBody(json.toByteArray(StandardCharsets.UTF_8), grpcPath)
    val utf8 = wireFormat.decodeBody(encodedOnce, true, grpcPath, null)
    val encodedTwice = wireFormat.encodeRequestBody(utf8, grpcPath)
    val decoded = String(utf8, StandardCharsets.UTF_8)
    assertTrue(decoded.contains("\"name\""))
    assertTrue(decoded.contains("Alice"))
    assertArrayEquals(encodedOnce, encodedTwice)
  }

  @Test
  fun decodeThenEncode_response_roundtrip() {
    val wireFormat = wireFormat()
    val lastRequestPath = "/pp.testsvc.Greeter/SayHello"
    val json = "{\n  \"message\": \"Hello\"\n}"
    val encodedOnce =
      wireFormat.encodeResponseBody(json.toByteArray(StandardCharsets.UTF_8), lastRequestPath)
    val utf8 = wireFormat.decodeBody(encodedOnce, false, null, lastRequestPath)
    val encodedTwice = wireFormat.encodeResponseBody(utf8, lastRequestPath)
    val decoded = String(utf8, StandardCharsets.UTF_8)
    assertTrue(decoded.contains("message") || decoded.contains("Hello"))
    assertArrayEquals(encodedOnce, encodedTwice)
  }
}
