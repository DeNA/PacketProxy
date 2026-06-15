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
import packetproxy.controller.ResendController
import packetproxy.model.OneShotPacket
import packetproxy.model.Packets

internal class ResendApiHandler(private val server: ManagementApiServer) {

  private companion object {
    /** サーバー応答を待つデフォルトの最大時間（ミリ秒）。body の `timeout_ms` で上書き可。 */
    const val DEFAULT_TIMEOUT_MS = 30_000L
  }

  /**
   * POST /api/packets/{id}/resend
   *
   * body (省略可):
   * ```json
   * {"data": "<base64>", "timeout_ms": 30000}
   * ```
   *
   * `data` 省略時は Packet の modified_data、なければ decoded_data を使用する。
   *
   * パケットを送信し、サーバー応答を待ってから、送信内容と応答を通信履歴（packets テーブル）に 保存する。レスポンスでは保存されたパケットの ID を返すので、`GET
   * /api/packets/{id}` で 内容を取得・相関できる。
   *
   * ```json
   * {"status":"completed","group":123,"requestPacketId":1024,"responsePacketId":1025}
   * ```
   *
   * 既存コネクションへの再送など応答を取得できないケースでは `responsePacketId` は省略される。
   */
  fun resend(packetId: Int, session: IHTTPSession): Response {
    val packet = Packets.getInstance().query(packetId) ?: return server.error404()

    val body = server.bodyJson(session)
    val data =
      if (body.has("data")) {
        Base64.getDecoder().decode(body["data"].asString)
      } else {
        packet.modifiedData ?: packet.decodedData ?: return server.error400("packet has no data")
      }
    val timeoutMs = if (body.has("timeout_ms")) body["timeout_ms"].asLong else DEFAULT_TIMEOUT_MS

    val oneshot = packet.getOneShotPacket(data)
    val responses = ResendController.getInstance().resendAndCollect(arrayOf(oneshot), timeoutMs)

    val group = ResendPersistence.nextGroup()
    val requestPacketId = ResendPersistence.persistRequest(oneshot, group)
    val responsePacketId = ResendPersistence.persistResponse(responses.getOrNull(0), group)

    return server.json200(
      mapOf(
        "status" to "completed",
        "group" to group,
        "requestPacketId" to requestPacketId,
        "responsePacketId" to responsePacketId,
      )
    )
  }

  /**
   * POST /api/packets/{id}/bulk-send
   *
   * base packet の接続情報を使い、指定したデータ配列を順番に送信する。
   *
   * body:
   * ```json
   * {"packets": [{"data": "<base64>"}, ...], "timeout_ms": 30000}
   * ```
   *
   * 各パケットを送信し、応答を待ってから送信内容と応答を通信履歴に保存する。 レスポンスの `results` は入力 `packets` と同じ順序で、各要素が保存された
   * リクエスト／応答パケットの ID を持つ。
   *
   * ```json
   * {"status":"completed","group":123,"count":2,
   *  "results":[{"index":0,"requestPacketId":1024,"responsePacketId":1025}, ...]}
   * ```
   */
  fun bulkSend(packetId: Int, session: IHTTPSession): Response {
    val packet = Packets.getInstance().query(packetId) ?: return server.error404()

    val body = server.bodyJson(session)
    val packetsArray =
      if (body.has("packets")) body.getAsJsonArray("packets")
      else return server.error400("'packets' array is required")

    if (packetsArray.size() == 0) return server.error400("'packets' array is empty")
    val timeoutMs = if (body.has("timeout_ms")) body["timeout_ms"].asLong else DEFAULT_TIMEOUT_MS

    val oneshots: Array<OneShotPacket> =
      packetsArray
        .map { el ->
          val obj = el.asJsonObject
          val data =
            if (obj.has("data")) {
              Base64.getDecoder().decode(obj["data"].asString)
            } else {
              packet.modifiedData ?: packet.decodedData ?: ByteArray(0)
            }
          packet.getOneShotPacket(data)
        }
        .toTypedArray()

    val responses = ResendController.getInstance().resendAndCollect(oneshots, timeoutMs)

    val group = ResendPersistence.nextGroup()
    val results =
      oneshots.indices.map { i ->
        mapOf(
          "index" to i,
          "requestPacketId" to ResendPersistence.persistRequest(oneshots[i], group),
          "responsePacketId" to ResendPersistence.persistResponse(responses.getOrNull(i), group),
        )
      }

    return server.json200(
      mapOf(
        "status" to "completed",
        "group" to group,
        "count" to oneshots.size,
        "results" to results,
      )
    )
  }
}
