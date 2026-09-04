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
package packetproxy.extensions.endpointoverview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import packetproxy.model.Packet

class EndpointVariantExtractorTest {
  @Test
  fun differingColumns_jsonBodyDiff_detectsIdColumn() {
    val first =
      createSummaryWithRequestBody(url = "https://example.com/api/users", body = """{"id":1}""")
    val second =
      createSummaryWithRequestBody(url = "https://example.com/api/users", body = """{"id":2}""")

    val rows = EndpointVariantExtractor.buildRows(listOf(first, second))
    val columns = EndpointVariantExtractor.differingColumns(rows)

    assertTrue(columns.contains("id"))
    assertEquals("1", rows[0].fields["id"])
    assertEquals("2", rows[1].fields["id"])
  }

  @Test
  fun differingColumns_queryDiff_detectsQueryParamColumn() {
    val first = createSummary("https://example.com/api/users?id=1", statusCodes = setOf("200"))
    val second = createSummary("https://example.com/api/users?id=2", statusCodes = setOf("200"))

    val rows = EndpointVariantExtractor.buildRows(listOf(first, second))
    val columns = EndpointVariantExtractor.differingColumns(rows)

    assertTrue(columns.contains("id"))
    assertEquals("1", rows[0].fields["id"])
    assertEquals("2", rows[1].fields["id"])
  }

  @Test
  fun differingColumns_noDiff_fallsBackToUrlColumn() {
    val first = createSummary("https://example.com/api/users", statusCodes = setOf("200"))
    val second = createSummary("https://example.com/api/users", statusCodes = setOf("200"))

    val rows = EndpointVariantExtractor.buildRows(listOf(first, second))
    val columns = EndpointVariantExtractor.differingColumns(rows)

    assertEquals(listOf("url"), columns)
  }

  @Test
  fun differingColumns_singleVariant_showsStatusWithoutUrl() {
    val summary = createSummary("https://example.com/api/users", statusCodes = setOf("200", "404"))

    val rows = EndpointVariantExtractor.buildRows(listOf(summary))
    val columns = EndpointVariantExtractor.differingColumns(rows)

    assertEquals(listOf("status"), columns)
  }

  private fun createSummary(url: String, statusCodes: Set<String> = setOf("200")): EndpointSummary {
    return EndpointSummary(
      method = "POST",
      url = url,
      host = "example.com",
      statusCodes = statusCodes.toMutableSet(),
      contentTypes = mutableSetOf(),
    )
  }

  private fun createSummaryWithRequestBody(url: String, body: String): EndpointSummary {
    val requestData =
      "POST /api/users HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json\r\n\r\n$body"
    val packet =
      Packet(
        8080,
        "127.0.0.1",
        12345,
        "93.184.216.34",
        443,
        "example.com",
        true,
        "HTTP",
        null,
        Packet.Direction.CLIENT,
        1,
        1L,
      )
    packet.setDecodedData(requestData.toByteArray())
    return EndpointSummary(
      method = "POST",
      url = url,
      host = "example.com",
      statusCodes = mutableSetOf("200"),
      contentTypes = mutableSetOf(),
      latestRequestPacket = packet,
    )
  }
}
