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

class EndpointAggregatorTest {
  @Test
  fun aggregateEndpoints_singlePair_returnsOneEndpoint() {
    val packets =
      listOf(
        createRequestPacket(group = 1L, method = "GET", host = "example.com", path = "/api/users"),
        createResponsePacket(group = 1L, statusCode = "200", contentType = "application/json"),
      )

    val endpoints = EndpointAggregator.aggregateEndpoints(packets)

    assertEquals(1, endpoints.size)
    val summary = endpoints.values.first()
    assertEquals("GET", summary.method)
    assertEquals("https://example.com/api/users", summary.url)
    assertEquals("example.com", summary.host)
    assertEquals(setOf("200"), summary.statusCodes)
    assertEquals(setOf("application/json"), summary.contentTypes)
  }

  @Test
  fun aggregateEndpoints_sameEndpointMultiplePairs_mergesStatusCodes() {
    val packets =
      listOf(
        createRequestPacket(group = 1L, method = "GET", host = "example.com", path = "/api/users"),
        createResponsePacket(group = 1L, statusCode = "200", contentType = "application/json"),
        createRequestPacket(group = 2L, method = "GET", host = "example.com", path = "/api/users"),
        createResponsePacket(group = 2L, statusCode = "404", contentType = "text/html"),
      )

    val endpoints = EndpointAggregator.aggregateEndpoints(packets)

    assertEquals(1, endpoints.size)
    val summary = endpoints.values.first()
    assertEquals(setOf("200", "404"), summary.statusCodes)
    assertEquals(setOf("application/json", "text/html"), summary.contentTypes)
  }

  @Test
  fun aggregateEndpoints_sameEndpointMultiplePairs_keepsLatestPacketPair() {
    val firstRequest =
      createRequestPacket(group = 1L, method = "GET", host = "example.com", path = "/api/users")
    val firstResponse =
      createResponsePacket(group = 1L, statusCode = "200", contentType = "application/json")
    val secondRequest =
      createRequestPacket(group = 2L, method = "GET", host = "example.com", path = "/api/users")
    val secondResponse =
      createResponsePacket(group = 2L, statusCode = "404", contentType = "text/html")
    val packets = listOf(firstRequest, firstResponse, secondRequest, secondResponse)

    val summary = EndpointAggregator.aggregateEndpoints(packets).values.first()

    assertEquals(secondRequest, summary.latestRequestPacket)
    assertEquals(secondResponse, summary.latestResponsePacket)
  }

  @Test
  fun aggregateEndpoints_responseWithoutRequest_isSkipped() {
    val packets =
      listOf(createResponsePacket(group = 1L, statusCode = "200", contentType = "application/json"))

    val endpoints = EndpointAggregator.aggregateEndpoints(packets)

    assertTrue(endpoints.isEmpty())
  }

  @Test
  fun aggregateEndpoints_unparseablePacket_isSkipped() {
    val packets =
      listOf(
        createPacket(
          group = 1L,
          direction = Packet.Direction.CLIENT,
          decodedData = "not http".toByteArray(),
        ),
        createPacket(
          group = 1L,
          direction = Packet.Direction.SERVER,
          decodedData = "still not http".toByteArray(),
        ),
      )

    val endpoints = EndpointAggregator.aggregateEndpoints(packets)

    assertTrue(endpoints.isEmpty())
  }

  @Test
  fun buildRequestMap_returnsOnlyClientPackets() {
    val request =
      createRequestPacket(group = 10L, method = "POST", host = "example.com", path = "/login")
    val response = createResponsePacket(group = 10L, statusCode = "302", contentType = "text/html")
    val requestMap = EndpointAggregator.buildRequestMap(listOf(request, response))

    assertEquals(1, requestMap.size)
    assertEquals(request, requestMap[10L])
  }

  private fun createRequestPacket(
    group: Long,
    method: String,
    host: String,
    path: String,
    query: String = "",
  ): Packet {
    val queryPart = if (query.isNotEmpty()) "?$query" else ""
    val data = "$method $path$queryPart HTTP/1.1\r\nHost: $host\r\n\r\n"
    return createPacket(
      group = group,
      direction = Packet.Direction.CLIENT,
      decodedData = data.toByteArray(),
    )
  }

  private fun createResponsePacket(group: Long, statusCode: String, contentType: String): Packet {
    val data = "HTTP/1.1 $statusCode OK\r\nContent-Type: $contentType\r\n\r\n"
    return createPacket(
      group = group,
      direction = Packet.Direction.SERVER,
      decodedData = data.toByteArray(),
    )
  }

  private fun createPacket(
    group: Long,
    direction: Packet.Direction,
    decodedData: ByteArray,
  ): Packet {
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
        direction,
        1,
        group,
      )
    packet.setDecodedData(decodedData)
    return packet
  }
}
