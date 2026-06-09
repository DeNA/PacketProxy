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
package packetproxy.cli.app

import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import packetproxy.AppInitializer
import packetproxy.ListenPortManager
import packetproxy.cli.api.ManagementApiServer
import packetproxy.util.LogMode
import packetproxy.util.Logging
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * PacketProxy サーバーを CLI で起動する前景ブロックモード。
 *
 * `--config` が指定された場合はその JSON を DB に適用してプロキシを起動する。 未指定の場合は GUI が保存した
 * `~/.packetproxy/db/resources.sqlite3` をそのまま使用し、 有効なプロキシをすべて起動する。
 *
 * 例:
 * ```
 * packetproxy server                                    # GUI の SQLite 設定をそのまま使用
 * packetproxy server --config settings.json             # JSON から設定をインポートして起動
 * packetproxy server --api-port 8888 --api-key mytoken  # 管理 API を有効化
 * ```
 */
@Command(
  name = "server",
  mixinStandardHelpOptions = true,
  description =
    [
      "Start PacketProxy proxy server in the foreground.",
      "Blocks until SIGINT or SIGTERM. Logs are written to stderr.",
      "",
      "Without --config, reads listen ports and servers from the GUI's SQLite",
      "database at ~/.packetproxy/db/resources.sqlite3.",
      "",
      "Use --api-port to enable the HTTP management API on localhost.",
    ],
)
class ServerCommand : Callable<Int> {

  @Option(
    names = ["--config", "-c"],
    description =
      [
        "Path to a settings JSON file exported from PacketProxy.",
        "Replaces the current SQLite config with the JSON contents.",
        "If omitted, the existing SQLite database is used as-is.",
      ],
  )
  var configPath: String? = null

  @Option(names = ["--no-log"], description = ["Suppress all log output."])
  var noLog: Boolean = false

  @Option(
    names = ["--api-port"],
    description =
      [
        "Enable the HTTP management API on this port (localhost only).",
        "Provides REST endpoints for packets, resend, vulcheck, and config.",
        "Example: --api-port 8888",
      ],
  )
  var apiPort: Int? = null

  @Option(
    names = ["--api-key"],
    description =
      [
        "Bearer token required for all API requests.",
        "Usage: Authorization: Bearer <token>",
        "If omitted, no authentication is required.",
      ],
  )
  var apiKey: String? = null

  override fun call(): Int {
    val logMode = if (noLog) LogMode.SILENT else LogMode.SERVER_STDERR

    AppInitializer.setLogMode(logMode)
    AppInitializer.setArgs(true, configPath)

    try {
      AppInitializer.initCore()
      AppInitializer.initGulp()
    } catch (e: Exception) {
      System.err.println("Failed to initialize: ${e.message}")
      return 1
    }

    // initComponents() (EncoderManager 等) の前に API サーバーを起動し、
    // 初期化中も /api/status で STARTING 状態を返せるようにする
    val apiServer =
      apiPort?.let { port ->
        try {
          val server = ManagementApiServer(port, apiKey)
          server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
          Logging.log("Management API started on http://127.0.0.1:$port/api/")
          if (apiKey == null) {
            Logging.log("Warning: --api-key not set. API is unauthenticated.")
          }
          server
        } catch (e: Exception) {
          System.err.println("Failed to start management API: ${e.message}")
          return 1
        }
      }

    try {
      AppInitializer.initComponents()
    } catch (e: Exception) {
      System.err.println("Failed to initialize components: ${e.message}")
      return 1
    }

    apiServer?.setReady()
    Logging.log("PacketProxy server started. Press Ctrl+C to stop.")

    val latch = CountDownLatch(1)

    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          apiServer?.setStopping()
          Logging.log("Shutting down PacketProxy server...")
          try {
            ListenPortManager.getInstance().stopAll()
          } catch (e: Exception) {
            Logging.err("Error during shutdown: ${e.message}")
          } finally {
            latch.countDown()
          }
        }
      )

    latch.await()
    return 0
  }
}
