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
package packetproxy.cli

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.Color.CYAN
import org.fusesource.jansi.Ansi.Color.GREEN
import org.jline.builtins.Completers.TreeCompleter
import org.jline.builtins.Completers.TreeCompleter.node
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.TerminalBuilder
import packetproxy.ListenPortManager
import packetproxy.common.ConfigIO
import packetproxy.common.Utils
import packetproxy.model.Database
import packetproxy.model.Packets
import packetproxy.util.Logging
import java.nio.file.Paths
import kotlin.concurrent.thread

object CLIMode {
    @JvmStatic
    @JvmOverloads
    fun run(settingJsonPath: String? = null) {
        try {
            val dbPath = Paths.get(
                System.getProperty("user.home"),
                ".packetproxy",
                "db",
                "resources.sqlite3"
            )
            Database.getInstance().openAt(dbPath.toString())
            Logging.log("データベースを初期化しました: ${dbPath}")
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            Logging.log("データベースの初期化に失敗しました。続行します: ${e.message}")
        }

        // Packetsを初期化（パケット受信に必要）
        try {
            Packets.getInstance(false)  // CLIモードでは履歴を復元しない
            Logging.log("Packetsを初期化しました")
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            Logging.log("Packetsの初期化に失敗しました。続行します: ${e.message}")
        }

        // ListenPortManagerを初期化（プロキシの自動管理を有効化）
        try {
            ListenPortManager.getInstance()
        } catch (e: Exception) {
            Logging.log("ListenPortManagerの初期化に失敗しました: ${e.message}")
        }

        thread {
            startProxyServer()
        }

        loadSettingsFromJson(settingJsonPath)

        // terminalのbuildを試行
        val terminal = try {
            TerminalBuilder.builder()
                .system(true)
                .build()
        } catch (e: Exception) {
            Logging.log("ターミナルの初期化に失敗しました。フォールバックモードに移行します: ${e.message}")
            fallbackInput()
            return
        }

        // readerのbuildを試行
        val reader = try {
            val commandCompleter = TreeCompleter(
                node("d", "decode"),
                node("e", "encode"),
                node("exit"),
                node("echo"),
                node("help"),
                node("status"),
                node("list", node(StringsCompleter("servers", "proxies"))),
                node("delete", node("server")),
                node("set", node(StringsCompleter("encoder", "proxy", "server"))),
            )

            LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(commandCompleter)
                .build()
        } catch (e: Exception) {
            Logging.log("LineReaderの初期化に失敗しました。フォールバックモードに移行します: ${e.message}")
            terminal.close()
            fallbackInput()
            return
        }

        println("=== CLI Mode ===")

        // リソース管理を改善: try-finallyで確実にクリーンアップ
        try {
            val promptEncoder = Ansi.ansi()
                .fg(GREEN).a("  B-E > ").reset()
                .toString()
            val promptDecoder = Ansi.ansi()
                .fg(CYAN).a("D-B   > ").reset()
                .toString()

            var ps1 = promptEncoder

            while (true) {
                try {
                    val line = reader.readLine(ps1).trim()
                    if (line.isEmpty()) continue

                    val (cmd, args) = CommandParser.parse(line)
                    when (cmd) {
                        "d", "decode" -> ps1 = promptDecoder
                        "e", "encode" -> ps1 = promptEncoder
                        "echo" -> println("Echo: ${args.joinToString(", ")}")
                        "exit" -> break
                        "help" -> {
                            println("利用可能なコマンド:")
                            println("  echo <text>              - テキストをエコー")
                            println("  exit                     - 終了")
                            println("  status                   - ステータス表示")
                            println("  set server <host> <port> [ssl] [encoder] [comment] - サーバーを設定")
                            println("  list servers              - サーバー一覧を表示")
                            println("  delete server <id>        - サーバーを削除")
                            println("  set proxy <port> [host] [target_port] - プロキシを設定")
                            println("  list proxies             - プロキシ一覧を表示")
                        }

                        "status" -> println("稼働中 (Port: 8080)")

                        "set" -> {
                            if (args.size <= 1) {
                                println("server | proxy | encoder")
                                continue
                            }

                            if (args[0] == "server") {
                                val host = args.getOrNull(1) ?: run {
                                    println("使用方法: set server <host> <port> [ssl] [encoder] [comment]")
                                    println("例: set server localhost 8080")
                                    println("例: set server example.com 443 ssl")
                                    println("例: set server proxy.example.com 3128 false \"\" \"Upstream Proxy\"")
                                    continue
                                }
                                val port = args.getOrNull(2)?.toIntOrNull() ?: run {
                                    println("ポート番号を指定してください")
                                    continue
                                }
                                val useSsl = args.getOrNull(3)?.toBoolean() ?: false
                                val encoder = args.getOrNull(4) ?: ""
                                val comment = args.getOrNull(5) ?: "CLI Mode"

                                val result = CLIProxyManager.createServer(host, port, useSsl, encoder, comment)
                                result.onSuccess { server ->
                                    println("サーバーを作成しました: ID=${server.getId()}, $host:$port")
                                }.onFailure { e ->
                                    println("エラー: ${e.message}")
                                }
                            } else if (args[0] == "encoder") {
                                continue
                            } else if (args[0] == "proxy") {
                                val listenPort = args.getOrNull(1)?.toIntOrNull() ?: run {
                                    println("使用方法: set proxy <listen_port> [target_host] [target_port]")
                                    println("例: set proxy 8081                    # localhost:8080に転送")
                                    println("例: set proxy 8081 localhost 9000     # localhost:9000に転送")
                                    println("例: set proxy 8081 proxy.example.com 3128  # proxy.example.com:3128に転送")
                                    continue
                                }

                                val targetHost = args.getOrNull(2) ?: "localhost"
                                val targetPort = args.getOrNull(3)?.toIntOrNull() ?: 8080

                                val result = CLIProxyManager.startProxy(listenPort, targetHost, targetPort)
                                result.onSuccess { message ->
                                    println(message)
                                }.onFailure { e ->
                                    println("エラー: ${e.message}")
                                }
                            }
                        }

                        "list" -> {
                            if (args.isEmpty()) {
                                println("servers | proxies")
                                continue
                            }
                            when (args[0]) {
                                "servers" -> {
                                    val servers = CLIProxyManager.listServers()
                                    if (servers.isEmpty()) {
                                        println("サーバーが登録されていません")
                                    } else {
                                        println("登録されているサーバー:")
                                        servers.forEach { (id, info) ->
                                            println("  ID: $id - $info")
                                        }
                                    }
                                }

                                "proxies" -> {
                                    val proxies = CLIProxyManager.listProxies()
                                    if (proxies.isEmpty()) {
                                        println("起動中のプロキシがありません")
                                    } else {
                                        println("起動中のプロキシ:")
                                        proxies.forEach { (port, target) ->
                                            println("  ポート: $port -> $target")
                                        }
                                    }
                                }

                                else -> println("不明なリストタイプ: ${args[0]}")
                            }
                        }

                        "delete" -> {
                            if (args.isEmpty() || args[0] != "server") {
                                println("使用方法: delete server <id>")
                                continue
                            }
                            val serverId = args.getOrNull(1)?.toIntOrNull() ?: run {
                                println("サーバーIDを指定してください")
                                continue
                            }
                            val result = CLIProxyManager.deleteServer(serverId)
                            result.onSuccess { message ->
                                println(message)
                            }.onFailure { e ->
                                println("エラー: ${e.message}")
                            }
                        }

                        else -> println("不明なコマンド: $line")
                    }
                } catch (e: UserInterruptException) {
                    // Ctrl + C: アプリケーション継続、改行
                    continue
                } catch (e: EndOfFileException) {
                    // Ctrl + D: アプリケーションを終了
                    println("${ps1}exit")
                    break
                } catch (e: Exception) {
                    Logging.errWithStackTrace(e)
                    e.printStackTrace()
                }
            }
        } finally {
            // リソースのクリーンアップ
            terminal.close()
            CLIProxyManager.stopAll()
        }
    }

    // フォールバック用の簡易入力処理
    private fun fallbackInput() {
        println("=== Fallback CLI Mode ===")

        val scanner = java.util.Scanner(System.`in`)
        while (true) {
            try {
                print("> ")
                val line = scanner.nextLine().trim()

                when (line) {
                    "exit" -> {
                        return
                    }

                    "status" -> println("稼働中 (Port: 8080)")
                    "help" -> println("使えるコマンド: exit, status, monitor")
                    "" -> continue
                    else -> println("不明なコマンド: $line")
                }
            } catch (e: Exception) {
                // Scannerのエラー（EOFなど）: 終了
                println("\n終了します。")
                return
            }
        }
    }

    /**
     * JSON設定ファイルを読み込んで適用
     */
    private fun loadSettingsFromJson(jsonPath: String?) {
        if (jsonPath?.isEmpty() ?: true) return

        try {
            val jsonBytes = Utils.readfile(jsonPath)
            val json = String(jsonBytes, Charsets.UTF_8)

            val configIO = ConfigIO()
            configIO.setOptions(json)

            Logging.log("設定ファイルを正常に読み込みました: $jsonPath")
        } catch (e: Exception) {
            Logging.err("設定ファイルの読み込みに失敗しました: ${e.message}", e)
            Logging.errWithStackTrace(e)
        }
    }

    private fun startProxyServer() {
        var i = 0
        Logging.log("Proxy Server listening on port 8080...")
        while (true) {
            Logging.log("hi: %d", i++)
            Thread.sleep(10000)
        }
    }
}
