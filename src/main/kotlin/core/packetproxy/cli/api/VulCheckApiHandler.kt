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

  private companion object {
    /** サーバー応答を待つデフォルトの最大時間（ミリ秒）。body の `timeout_ms` で上書き可。 */
    const val DEFAULT_TIMEOUT_MS = 30_000L
  }

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
   * 各 Generator がペイロードを生成し、対象範囲を置換した OneShotPacket を順番に送信する。 送信後にサーバー応答を待ち、送信内容と応答を通信履歴（packets
   * テーブル）に保存する。 レスポンスの `results` は送信した Generator ごとに 1 要素で、保存されたリクエスト／応答 パケットの ID と Generator 名を持つ。
   *
   * ```json
   * {"status":"completed","group":123,"sent":2,
   *  "results":[{"index":0,"generator":"NegativeNumber","requestPacketId":1024,"responsePacketId":1025}, ...]}
   * ```
   *
   * body の `timeout_ms` で応答待ちの最大時間（ミリ秒）を指定できる。
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
    val timeoutMs = if (body.has("timeout_ms")) body["timeout_ms"].asLong else DEFAULT_TIMEOUT_MS

    if (rangeStart < 0 || rangeEnd <= rangeStart) {
      return server.error400("invalid range: range_end must be greater than range_start")
    }

    val packet = Packets.getInstance().query(packetId) ?: return server.error404()
    val baseData =
      packet.modifiedData ?: packet.decodedData ?: return server.error400("packet has no data")
    if (rangeEnd > baseData.size) {
      return server.error400("range_end exceeds packet data length (${baseData.size})")
    }

    val vulChecker = manager.createInstance(checkerName)
    val prefix = baseData.copyOfRange(0, rangeStart)
    val suffix = baseData.copyOfRange(rangeEnd, baseData.size)
    val original = String(baseData.copyOfRange(rangeStart, rangeEnd))

    val oneshots = mutableListOf<OneShotPacket>()
    val generatorNames = mutableListOf<String>()
    for (generator in vulChecker.generators) {
      try {
        val payload = generator.generate(original).toByteArray()
        val newData = prefix + payload + suffix
        oneshots.add(packet.getOneShotPacket(newData))
        generatorNames.add(generator.name)
      } catch (_: Exception) {
        // generator がこの入力に対応していない場合はスキップ
      }
    }

    if (oneshots.isEmpty()) {
      return server.json200(
        mapOf("status" to "completed", "sent" to 0, "results" to emptyList<Any>())
      )
    }

    val responses =
      ResendController.getInstance().resendAndCollect(oneshots.toTypedArray(), timeoutMs)

    val group = ResendPersistence.nextGroup()
    val results =
      oneshots.indices.map { i ->
        mapOf(
          "index" to i,
          "generator" to generatorNames[i],
          "requestPacketId" to ResendPersistence.persistRequest(oneshots[i], group),
          "responsePacketId" to ResendPersistence.persistResponse(responses.getOrNull(i), group),
        )
      }

    return server.json200(
      mapOf(
        "status" to "completed",
        "group" to group,
        "sent" to oneshots.size,
        "results" to results,
      )
    )
  }
}
