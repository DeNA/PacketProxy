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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import packetproxy.model.Database

/** `GET /api/status` が返すサーバーの状態。 */
enum class ServerPhase {
  /** コンポーネント初期化中。EncoderManager 等のロードが完了していない。 */
  STARTING,

  /** 全コンポーネント初期化済み。プロキシが通信を受け付けている。 */
  READY,

  /** シャットダウン処理中。SIGINT / SIGTERM 受信後。 */
  STOPPING,
}

private data class ServerState(val phase: ServerPhase, val startedAt: Long, val readyAt: Long?)

/**
 * packetproxy server の管理 HTTP API サーバー。
 *
 * localhost のみにバインドし、オプションで Bearer トークン認証を適用する。 NanoHTTPD ベースの実装で既存の ConfigHttpServer と同じパターンを踏襲する。
 *
 * `/api/status` のみ認証不要（起動ポーリング用途）。
 *
 * 起動例:
 * ```
 * packetproxy server --api-port 8888 --api-key mytoken
 * ```
 */
class ManagementApiServer(port: Int, private val apiKey: String?) : NanoHTTPD("127.0.0.1", port) {

  @Volatile private var state = ServerState(ServerPhase.STARTING, System.currentTimeMillis(), null)

  /** initComponents() 完了後に呼ぶ。status が READY に遷移する。 */
  fun setReady() {
    state = state.copy(phase = ServerPhase.READY, readyAt = System.currentTimeMillis())
  }

  /** シャットダウンフック内で呼ぶ。status が STOPPING に遷移する。 */
  fun setStopping() {
    state = state.copy(phase = ServerPhase.STOPPING)
  }

  internal val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

  private val packetHandler = PacketApiHandler(this)
  private val resendHandler = ResendApiHandler(this)
  private val vulCheckHandler = VulCheckApiHandler(this)
  private val configHandler = ConfigApiHandler(this)

  override fun serve(session: IHTTPSession): Response {
    if (session.method == Method.OPTIONS) return corsPreflightOk()
    if (!isAuthorized(session)) return unauthorized()
    return try {
      route(session)
    } catch (e: Exception) {
      error500(e.message ?: "internal error")
    }
  }

  private fun statusResponse(): Response {
    val s = state
    val now = System.currentTimeMillis()
    val body =
      buildMap<String, Any?> {
        put("status", s.phase.name)
        put("startedAt", s.startedAt)
        if (s.readyAt != null) {
          put("readyAt", s.readyAt)
          put("uptimeSec", (now - s.readyAt) / 1000)
        }
      }
    return withCors(
      NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", gson.toJson(body))
    )
  }

  private fun dbDownload(): Response {
    val tmp = java.nio.file.Files.createTempFile("packetproxy_db_", ".sqlite3")
    return try {
      // Database.Save() は DB 書き込みを一時停止してからファイルをコピーし、完了後に再開する
      Database.getInstance().Save(tmp.toString())
      val bytes = java.nio.file.Files.readAllBytes(tmp)
      val resp =
        NanoHTTPD.newFixedLengthResponse(
          Status.OK,
          "application/octet-stream",
          java.io.ByteArrayInputStream(bytes),
          bytes.size.toLong(),
        )
      resp.addHeader("Content-Disposition", "attachment; filename=\"resources.sqlite3\"")
      resp.addHeader("Access-Control-Allow-Origin", "*")
      resp
    } finally {
      java.nio.file.Files.deleteIfExists(tmp)
    }
  }

  private fun route(session: IHTTPSession): Response {
    val path = session.uri
    val method = session.method
    return when {
      path == "/api/status" && method == Method.GET -> statusResponse()
      path == "/api/packets" && method == Method.GET -> packetHandler.list(session)
      path.matches(Regex("/api/packets/\\d+")) && method == Method.GET ->
        packetHandler.get(lastSegmentInt(path))
      path.matches(Regex("/api/packets/\\d+/resend")) && method == Method.POST ->
        resendHandler.resend(secondLastSegmentInt(path), session)
      path.matches(Regex("/api/packets/\\d+/bulk-send")) && method == Method.POST ->
        resendHandler.bulkSend(secondLastSegmentInt(path), session)
      path == "/api/vulcheckers" && method == Method.GET -> vulCheckHandler.list()
      path.matches(Regex("/api/vulcheckers/[^/]+/run")) && method == Method.POST ->
        vulCheckHandler.run(secondLastSegment(path), session)
      path == "/api/config" && method == Method.GET -> configHandler.getConfig()
      path == "/api/config" && method == Method.PUT -> configHandler.putConfig(session)
      path == "/api/listenports" -> configHandler.listenPorts(method, null, session)
      path.matches(Regex("/api/listenports/\\d+")) ->
        configHandler.listenPorts(method, lastSegmentInt(path), session)
      path == "/api/servers" -> configHandler.servers(method, null, session)
      path.matches(Regex("/api/servers/\\d+")) ->
        configHandler.servers(method, lastSegmentInt(path), session)
      path == "/api/modifications" -> configHandler.modifications(method, null, session)
      path.matches(Regex("/api/modifications/\\d+")) ->
        configHandler.modifications(method, lastSegmentInt(path), session)
      path == "/api/db" && method == Method.GET -> dbDownload()
      else -> error404()
    }
  }

  private fun isAuthorized(session: IHTTPSession): Boolean {
    if (apiKey == null) return true
    val auth = session.headers["authorization"] ?: return false
    return auth == "Bearer $apiKey"
  }

  // --- response builders ---

  internal fun json200(obj: Any): Response =
    withCors(NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", gson.toJson(obj)))

  internal fun jsonOk(): Response = json200(mapOf("status" to "ok"))

  internal fun error400(message: String): Response =
    withCors(
      NanoHTTPD.newFixedLengthResponse(
        Status.BAD_REQUEST,
        "application/json",
        gson.toJson(mapOf("error" to message)),
      )
    )

  internal fun error404(): Response =
    withCors(
      NanoHTTPD.newFixedLengthResponse(
        Status.NOT_FOUND,
        "application/json",
        gson.toJson(mapOf("error" to "not found")),
      )
    )

  private fun error500(message: String): Response =
    withCors(
      NanoHTTPD.newFixedLengthResponse(
        Status.INTERNAL_ERROR,
        "application/json",
        gson.toJson(mapOf("error" to message)),
      )
    )

  private fun unauthorized(): Response =
    withCors(
      NanoHTTPD.newFixedLengthResponse(
        Status.UNAUTHORIZED,
        "application/json",
        gson.toJson(mapOf("error" to "unauthorized")),
      )
    )

  private fun corsPreflightOk(): Response {
    val res = NanoHTTPD.newFixedLengthResponse(Status.OK, MIME_HTML, null)
    res.addHeader("Access-Control-Allow-Origin", "*")
    res.addHeader("Access-Control-Allow-Headers", "Authorization,Content-Type")
    res.addHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
    res.addHeader("Access-Control-Allow-Private-Network", "true")
    res.addHeader("Access-Control-Max-Age", "86400")
    return res
  }

  private fun withCors(res: Response): Response {
    res.addHeader("Access-Control-Allow-Origin", "*")
    return res
  }

  // --- request helpers ---

  internal fun bodyJson(session: IHTTPSession): JsonObject {
    val map = HashMap<String, String>()
    try {
      session.parseBody(map)
    } catch (_: Exception) {}
    val raw = map["postData"] ?: return JsonObject()
    return try {
      gson.fromJson(raw, JsonObject::class.java) ?: JsonObject()
    } catch (_: Exception) {
      JsonObject()
    }
  }

  // --- path segment helpers ---

  private fun lastSegmentInt(path: String): Int = path.trimEnd('/').substringAfterLast('/').toInt()

  private fun secondLastSegmentInt(path: String): Int {
    val parts = path.trimEnd('/').split('/')
    return parts[parts.size - 2].toInt()
  }

  private fun secondLastSegment(path: String): String {
    val parts = path.trimEnd('/').split('/')
    return parts[parts.size - 2]
  }
}
