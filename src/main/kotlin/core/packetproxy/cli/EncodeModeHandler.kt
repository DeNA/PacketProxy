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
import org.fusesource.jansi.Ansi.Color.GREEN
import org.jline.builtins.Completers.TreeCompleter
import org.jline.builtins.Completers.TreeCompleter.node
import org.jline.reader.impl.completer.StringsCompleter

/**
 * Encode Modeのハンドラー
 * Binary to Encoded (B-E) モード
 */
class EncodeModeHandler : CLIModeHandler {
    override fun getModeName(): String = "encode"

    override fun getPrompt(): String {
        return Ansi.ansi()
            .fg(GREEN).a("  B-E > ").reset()
            .toString()
    }

    override fun createCompleter(): TreeCompleter {
        return TreeCompleter(
            node("d", "decode"),
            node("e", "encode"),
            node("exit"),
            node("echo"),
            node("help"),
            node("status"),
            node("list", node(StringsCompleter("servers", "proxies"))),
            node("delete", node("server")),
            node(
                "set",
                node("server"),
                node("proxy"),
                node("encoder")
            ),
        )
    }

    override fun handleCommand(cmd: String, args: List<String>): Boolean {
        return when (cmd) {
            "e", "encode" -> {
                // 既にencode modeなので何もしない
                true
            }

            "d", "decode" -> {
                // decode modeへの切り替えはCLIModeで処理
                false
            }

            "echo" -> {
                println("Echo: ${args.joinToString(", ")}")
                true
            }

            "exit" -> {
                // exitはCLIModeで処理
                false
            }

            "help" -> {
                println(getHelpMessage())
                true
            }

            "status" -> {
                println("稼働中 (Port: 8080) [Encode Mode]")
                true
            }

            "set" -> {
                handleSetCommand(args)
                true
            }

            "list" -> {
                handleListCommand(args)
                true
            }

            "delete" -> {
                handleDeleteCommand(args)
                true
            }

            else -> false
        }
    }

    private fun handleSetCommand(args: List<String>) {
        if (args.size <= 1) {
            println("server | proxy | encoder")
            return
        }

        when (args[0]) {
            "server" -> {
                val host = args.getOrNull(1) ?: run {
                    println("使用方法: set server <host> <port> [ssl] [encoder] [comment]")
                    println("例: set server localhost 8080")
                    println("例: set server example.com 443 ssl")
                    return
                }
                val port = args.getOrNull(2)?.toIntOrNull() ?: run {
                    println("ポート番号を指定してください")
                    return
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
            }

            "encoder" -> {
                // Encode modeでのencoder設定処理
                println("Encode mode: encoder設定は未実装です")
            }

            "proxy" -> {
                val listenPort = args.getOrNull(1)?.toIntOrNull() ?: run {
                    println("使用方法: set proxy <listen_port> [target_host] [target_port]")
                    println("例: set proxy 8081                    # localhost:8080に転送")
                    return
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
    }

    private fun handleListCommand(args: List<String>) {
        if (args.isEmpty()) {
            println("servers | proxies")
            return
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

    private fun handleDeleteCommand(args: List<String>) {
        if (args.isEmpty() || args[0] != "server") {
            println("使用方法: delete server <id>")
            return
        }
        val serverId = args.getOrNull(1)?.toIntOrNull() ?: run {
            println("サーバーIDを指定してください")
            return
        }
        val result = CLIProxyManager.deleteServer(serverId)
        result.onSuccess { message ->
            println(message)
        }.onFailure { e ->
            println("エラー: ${e.message}")
        }
    }

    override fun getHelpMessage(): String {
        return """
利用可能なコマンド (Encode Mode):
  echo <text>              - テキストをエコー
  exit                     - 終了
  status                   - ステータス表示
  d, decode               - Decode Modeに切り替え
  e, encode               - Encode Modeに切り替え（現在のモード）
  set server <host> <port> [ssl] [encoder] [comment] - サーバーを設定
  list servers              - サーバー一覧を表示
  delete server <id>        - サーバーを削除
  set proxy <port> [host] [target_port] - プロキシを設定
  list proxies             - プロキシ一覧を表示
  set encoder              - エンコーダーを設定（未実装）
        """.trimIndent()
    }
}

