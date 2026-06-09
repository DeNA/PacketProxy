/*
 * Copyright 2025 DeNA Co., Ltd.
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
package packetproxy.cli.api

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import java.util.Base64
import packetproxy.model.Packet
import packetproxy.model.Packets

internal class PacketApiHandler(private val server: ManagementApiServer) {

  /**
   * GET /api/packets
   *
   * クエリパラメータ:
   * - offset (default 0)
   * - limit (default 100, max 1000)
   * - filter GUI の FilterTextParser と同一のフィルター式（例: `server_port==443&&encode==HTTP2`）
   * - q フルテキスト検索文字列（レガシー。filter=full_text==<q> と等価）
   *
   * データフィールド（decoded_data など）は含まない。詳細は GET /api/packets/{id} を使う。
   */
  fun list(session: IHTTPSession): Response {
    val params = session.parameters
    val filterExpr = params["filter"]?.firstOrNull()
    val q = params["q"]?.firstOrNull()
    val offset = params["offset"]?.firstOrNull()?.toLongOrNull() ?: 0L
    val limit = (params["limit"]?.firstOrNull()?.toLongOrNull() ?: 100L).coerceAtMost(1000L)

    val packets =
      when {
        !filterExpr.isNullOrBlank() -> {
          val whereClause =
            try {
              PacketFilterQuery.toSql(filterExpr)
            } catch (e: IllegalArgumentException) {
              return server.error400("Invalid filter: ${e.message}")
            }
          Packets.getInstance().querySummaryRangeFiltered(offset, limit, whereClause)
        }
        !q.isNullOrBlank() -> {
          val escaped = q.replace("'", "''")
          Packets.getInstance()
            .querySummaryRangeFiltered(offset, limit, "decoded_data GLOB '*${escaped}*'")
        }
        else -> Packets.getInstance().querySummaryRange(offset, limit)
      }

    val data = packets.map { it.toSummary() }
    return server.json200(
      mapOf("data" to data, "offset" to offset, "limit" to limit).let { base ->
        if (!filterExpr.isNullOrBlank()) base + mapOf("filter" to filterExpr) else base
      }
    )
  }

  /**
   * GET /api/packets/{id}
   *
   * decoded_data / modified_data / sent_data / received_data を Base64 で返す。
   */
  fun get(id: Int): Response {
    val packet = Packets.getInstance().query(id) ?: return server.error404()
    return server.json200(mapOf("data" to packet.toDetail()))
  }

  // --- private DTOs ---

  private fun Packet.toSummary() =
    mapOf(
      "id" to id,
      "direction" to direction?.name,
      "listenPort" to listenPort,
      "clientIp" to clientIP,
      "serverIp" to serverIP,
      "serverPort" to serverPort,
      "serverName" to serverName,
      "encoderName" to encoder,
      "modified" to getModified(),
      "resend" to getResend(),
      "date" to date?.time,
      "color" to color,
    )

  private fun Packet.toDetail() =
    mapOf(
      "id" to id,
      "direction" to direction?.name,
      "listenPort" to listenPort,
      "clientIp" to clientIP,
      "clientPort" to clientPort,
      "serverIp" to serverIP,
      "serverPort" to serverPort,
      "serverName" to serverName,
      "useSsl" to getUseSSL(),
      "encoderName" to encoder,
      "alpn" to alpn,
      "modified" to getModified(),
      "resend" to getResend(),
      "date" to date?.time,
      "conn" to conn,
      "group" to group,
      "color" to color,
      "decodedData" to decodedData?.b64(),
      "modifiedData" to modifiedData?.b64(),
      "sentData" to sentData?.b64(),
      "receivedData" to receivedData?.b64(),
    )

  private fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)
}
