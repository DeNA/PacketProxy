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
import packetproxy.VulCheckerManager
import packetproxy.controller.ResendController
import packetproxy.model.OneShotPacket
import packetproxy.model.Packets

internal class VulCheckApiHandler(private val server: ManagementApiServer) {

  /**
   * GET /api/vulcheckers
   *
   * 利用可能な VulChecker 名と、各チェッカーの Generator 名一覧を返す。
   *
   * レスポンス例:
   * ```json
   * {"data": [{"name":"Number","generators":["NegativeNumber","Zero",...]}, ...]}
   * ```
   */
  fun list(): Response {
    val manager = VulCheckerManager.getInstance()
    val checkers =
      manager.vulCheckerNameList.map { name ->
        val checker = manager.createInstance(name)
        mapOf("name" to name, "generators" to checker.generators.map { it.name })
      }
    return server.json200(mapOf("data" to checkers))
  }

  /**
   * POST /api/vulcheckers/{name}/run
   *
   * 指定した VulChecker のすべての Generator を、パケットの指定範囲に適用して送信する。 GUIVulCheckManager と同等のロジックを GUI
   * 非依存で実装している。
   *
   * body:
   * ```json
   * {"packet_id": 42, "range_start": 10, "range_end": 20}
   * ```
   * - `packet_id`: ベースにするパケットの ID
   * - `range_start` / `range_end`: データの置換対象バイト範囲（0始まり）
   *
   * 各 Generator がペイロードを生成し、対象範囲を置換した OneShotPacket を順番に送信する。 送信は非同期で開始され、即座に
   * `{"status":"accepted","sent":N}` を返す。
   */
  fun run(checkerName: String, session: IHTTPSession): Response {
    val manager = VulCheckerManager.getInstance()
    if (!manager.allVulCheckers.containsKey(checkerName)) {
      return server.error404()
    }

    val body = server.bodyJson(session)
    val packetId = body["packet_id"]?.asInt ?: return server.error400("'packet_id' is required")
    val rangeStart =
      body["range_start"]?.asInt ?: return server.error400("'range_start' is required")
    val rangeEnd = body["range_end"]?.asInt ?: return server.error400("'range_end' is required")

    if (rangeStart < 0 || rangeEnd <= rangeStart) {
      return server.error400("invalid range: range_end must be greater than range_start")
    }

    val packet = Packets.getInstance().query(packetId) ?: return server.error404()
    val baseData =
      packet.modifiedData ?: packet.decodedData ?: return server.error400("packet has no data")

    val vulChecker = manager.createInstance(checkerName)
    val prefix = baseData.copyOfRange(0, rangeStart)
    val suffix = baseData.copyOfRange(rangeEnd, baseData.size)
    val original = String(baseData.copyOfRange(rangeStart, rangeEnd))

    val oneshots = mutableListOf<OneShotPacket>()
    for (generator in vulChecker.generators) {
      try {
        val payload = generator.generate(original).toByteArray()
        val newData = prefix + payload + suffix
        oneshots.add(packet.getOneShotPacket(newData))
      } catch (_: Exception) {
        // generator がこの入力に対応していない場合はスキップ
      }
    }

    if (oneshots.isEmpty()) {
      return server.json200(mapOf("status" to "accepted", "sent" to 0))
    }

    val worker = ResendController.ResendWorker(oneshots.toTypedArray())
    ResendController.getInstance().resend(worker)
    return server.json200(mapOf("status" to "accepted", "sent" to oneshots.size))
  }
}
