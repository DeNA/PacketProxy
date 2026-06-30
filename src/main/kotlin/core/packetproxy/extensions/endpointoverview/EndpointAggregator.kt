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

import packetproxy.http.Http
import packetproxy.model.Packet

object EndpointAggregator {
  fun buildRequestMap(packets: List<Packet>): Map<Long, Packet> {
    val requestMap = mutableMapOf<Long, Packet>()

    for (packet in packets) {
      if (packet.direction != Packet.Direction.CLIENT) {
        continue
      }
      requestMap[packet.group] = packet
    }

    return requestMap
  }

  fun aggregateEndpoints(packets: List<Packet>): Map<String, EndpointSummary> {
    val requestMap = buildRequestMap(packets)
    val endpointMap = mutableMapOf<String, EndpointSummary>()

    for (responsePacket in packets) {
      if (responsePacket.direction != Packet.Direction.SERVER) {
        continue
      }

      val requestPacket = requestMap[responsePacket.group] ?: continue
      mergePacketPair(endpointMap, requestPacket, responsePacket)
    }

    return endpointMap
  }

  private fun mergePacketPair(
    endpointMap: MutableMap<String, EndpointSummary>,
    requestPacket: Packet,
    responsePacket: Packet,
  ) {
    try {
      val requestHttp = Http.create(requestPacket.decodedData)
      val responseHttp = Http.create(responsePacket.decodedData)

      val method = requestHttp.method ?: return
      val host = requestHttp.header.getValue("Host").orElse(requestPacket.serverName) ?: return
      if (!requestHttp.header.getValue("Host").isPresent) {
        requestHttp.updateHeader("Host", host)
      }
      val url = requestHttp.getURL(requestPacket.serverPort, requestPacket.useSSL) ?: return
      val statusCode = responseHttp.statusCode ?: return

      val contentType = responseHttp.header.getValue("Content-Type").orElse("")
      val endpointKey = "$method $url"

      val summary =
        endpointMap.getOrPut(endpointKey) {
          EndpointSummary(method = method, url = url, host = host)
        }

      summary.statusCodes.add(statusCode)
      if (contentType.isNotEmpty()) {
        summary.contentTypes.add(contentType)
      }
      summary.latestRequestPacket = requestPacket
      summary.latestResponsePacket = responsePacket
    } catch (_: Exception) {
      // Skip packets that cannot be parsed as HTTP.
    }
  }
}
