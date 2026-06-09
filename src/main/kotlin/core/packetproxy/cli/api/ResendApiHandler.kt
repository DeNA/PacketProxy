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

  /**
   * POST /api/packets/{id}/resend
   *
   * body (省略可):
   * ```json
   * {"data": "<base64>"}
   * ```
   *
   * `data` 省略時は Packet の modified_data、なければ decoded_data を使用する。
   *
   * 送信は非同期で開始され、即座に `{"status":"accepted"}` を返す。
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

    val oneshot = packet.getOneShotPacket(data)
    ResendController.getInstance().resend(oneshot, 1, false)
    return server.json200(mapOf("status" to "accepted"))
  }

  /**
   * POST /api/packets/{id}/bulk-send
   *
   * base packet の接続情報を使い、指定したデータ配列を順番に送信する。
   *
   * body:
   * ```json
   * {"packets": [{"data": "<base64>"}, ...]}
   * ```
   *
   * 送信は非同期で開始され、即座に `{"status":"accepted","count":N}` を返す。
   */
  fun bulkSend(packetId: Int, session: IHTTPSession): Response {
    val packet = Packets.getInstance().query(packetId) ?: return server.error404()

    val body = server.bodyJson(session)
    val packetsArray =
      if (body.has("packets")) body.getAsJsonArray("packets")
      else return server.error400("'packets' array is required")

    if (packetsArray.size() == 0) return server.error400("'packets' array is empty")

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

    val worker = ResendController.ResendWorker(oneshots)
    ResendController.getInstance().resend(worker)
    return server.json200(mapOf("status" to "accepted", "count" to oneshots.size))
  }
}
