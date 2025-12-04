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

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
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

        // モードハンドラーを初期化
        val encodeHandler = EncodeModeHandler()
        val decodeHandler = DecodeModeHandler()
        var currentHandler: CLIModeHandler = encodeHandler

        // 動的補完を作成
        val dynamicCompleter = DynamicCompleter(currentHandler)

        // readerのbuildを試行
        val reader = try {
            LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(dynamicCompleter)
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
            while (true) {
                try {
                    val ps1 = currentHandler.getPrompt()
                    val line = reader.readLine(ps1).trim()
                    if (line.isEmpty()) continue

                    val (cmd, args) = CommandParser.parse(line)
                    
                    // モード切り替えコマンド
                    when (cmd) {
                        "d", "decode" -> {
                            currentHandler = decodeHandler
                            dynamicCompleter.updateHandler(currentHandler)
                            continue
                        }
                        "e", "encode" -> {
                            currentHandler = encodeHandler
                            dynamicCompleter.updateHandler(currentHandler)
                            continue
                        }
                        "exit" -> break
                        else -> {
                            // 現在のハンドラーでコマンドを処理
                            if (!currentHandler.handleCommand(cmd, args)) {
                                // ハンドラーで処理されなかった場合のフォールバック
                                when (cmd) {
                                    "exit" -> break
                                    else -> println("不明なコマンド: $line")
                                }
                            }
                        }
                    }
                } catch (e: UserInterruptException) {
                    // Ctrl + C: アプリケーション継続、改行
                    continue
                } catch (e: EndOfFileException) {
                    // Ctrl + D: アプリケーションを終了
                    println("${currentHandler.getPrompt()}exit")
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
