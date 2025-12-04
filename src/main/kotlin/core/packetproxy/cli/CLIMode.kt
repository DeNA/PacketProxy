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
import packetproxy.EncoderManager
import packetproxy.ListenPortManager
import packetproxy.VulCheckerManager
import packetproxy.common.ClientKeyManager
import packetproxy.common.ConfigIO
import packetproxy.common.Utils
import packetproxy.model.Database
import packetproxy.model.Packets
import packetproxy.util.Logging
import java.nio.file.Paths

/**
 * CLI初期化結果
 */
private data class CLIInitResult(
    val success: Boolean,
    val message: String? = null
)

object CLIMode {
    @JvmStatic
    @JvmOverloads
    fun run(settingJsonPath: String? = null) {
        // 初期化処理を実行
        val initResult = initializeCLIComponents()
        if (!initResult.success) {
            Logging.log("初期化に失敗しました: ${initResult.message}")
            // 初期化に失敗しても続行可能な場合は続行
        }

        // 設定ファイルを読み込む（ListenPortManager初期化後）
        val settingsLoaded = loadSettingsFromJson(settingJsonPath)
        if (settingsLoaded) {
            Logging.log("設定ファイルからプロキシ設定を読み込みました。有効なプロキシは自動的に開始されます。")
        }

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
                        "s", "switch" -> {
                            currentHandler = if (currentHandler == decodeHandler) {
                                encodeHandler
                            } else {
                                decodeHandler
                            }
                            dynamicCompleter.updateHandler(currentHandler)
                            continue
                        }

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

                        else -> {
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
                    // Ctrl + C: 継続、改行
                    continue
                } catch (e: EndOfFileException) {
                    // Ctrl + D: 終了
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
                if (line.isEmpty()) continue

                val (cmd, args) = CommandParser.parse(line)

                when (cmd) {
                    "exit" -> return
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
     * CLIコンポーネントを初期化
     * GUIモードと同様の順序で初期化を行う
     */
    private fun initializeCLIComponents(): CLIInitResult {
        // 1. データベースを初期化
        val dbResult = initializeDatabase()
        if (!dbResult.success) {
            return CLIInitResult(false, "データベースの初期化に失敗しました")
        }

        // 2. Packetsを初期化（パケット受信に必要）
        val packetsResult = initializePackets()
        if (!packetsResult.success) {
            return CLIInitResult(false, "Packetsの初期化に失敗しました")
        }

        // 3. ClientKeyManagerを初期化
        val clientKeyResult = initializeClientKeyManager()
        if (!clientKeyResult.success) {
            Logging.log("ClientKeyManagerの初期化に失敗しましたが、続行します")
        }

        // 4. EncoderManagerを初期化（encoderのロードに時間がかかるため事前にロード）
        val encoderResult = initializeEncoderManager()
        if (!encoderResult.success) {
            Logging.log("EncoderManagerの初期化に失敗しましたが、続行します")
        }

        // 5. VulCheckerManagerを初期化
        val vulCheckerResult = initializeVulCheckerManager()
        if (!vulCheckerResult.success) {
            Logging.log("VulCheckerManagerの初期化に失敗しましたが、続行します")
        }

        // 6. ListenPortManagerを初期化（プロキシの自動管理を有効化）
        // この時点で有効なListenPortが自動的に開始される
        val listenPortResult = initializeListenPortManager()
        if (!listenPortResult.success) {
            return CLIInitResult(false, "ListenPortManagerの初期化に失敗しました")
        }

        return CLIInitResult(true, "すべてのコンポーネントの初期化が完了しました")
    }

    /**
     * データベースを初期化
     */
    private fun initializeDatabase(): CLIInitResult {
        return try {
            val dbPath = Paths.get(
                System.getProperty("user.home"),
                ".packetproxy",
                "db",
                "resources.sqlite3"
            )
            Database.getInstance().openAt(dbPath.toString())
            Logging.log("データベースを初期化しました: $dbPath")
            CLIInitResult(true)
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            CLIInitResult(false, "データベースの初期化に失敗しました: ${e.message}")
        }
    }

    /**
     * Packetsを初期化
     */
    private fun initializePackets(): CLIInitResult {
        return try {
            Packets.getInstance(false)  // CLIモードでは履歴を復元しない
            Logging.log("Packetsを初期化しました")
            CLIInitResult(true)
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            CLIInitResult(false, "Packetsの初期化に失敗しました: ${e.message}")
        }
    }

    /**
     * ClientKeyManagerを初期化
     */
    private fun initializeClientKeyManager(): CLIInitResult {
        return try {
            ClientKeyManager.initialize()
            Logging.log("ClientKeyManagerを初期化しました")
            CLIInitResult(true)
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            CLIInitResult(false, "ClientKeyManagerの初期化に失敗しました: ${e.message}")
        }
    }

    /**
     * EncoderManagerを初期化
     */
    private fun initializeEncoderManager(): CLIInitResult {
        return try {
            EncoderManager.getInstance()
            Logging.log("EncoderManagerを初期化しました")
            CLIInitResult(true)
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            CLIInitResult(false, "EncoderManagerの初期化に失敗しました: ${e.message}")
        }
    }

    /**
     * VulCheckerManagerを初期化
     */
    private fun initializeVulCheckerManager(): CLIInitResult {
        return try {
            VulCheckerManager.getInstance()
            Logging.log("VulCheckerManagerを初期化しました")
            CLIInitResult(true)
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            CLIInitResult(false, "VulCheckerManagerの初期化に失敗しました: ${e.message}")
        }
    }

    /**
     * ListenPortManagerを初期化
     * この時点で有効なListenPortが自動的に開始される
     */
    private fun initializeListenPortManager(): CLIInitResult {
        return try {
            ListenPortManager.getInstance()
            Logging.log("ListenPortManagerを初期化しました（有効なプロキシは自動的に開始されます）")
            CLIInitResult(true)
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            CLIInitResult(false, "ListenPortManagerの初期化に失敗しました: ${e.message}")
        }
    }

    /**
     * JSON設定ファイルを読み込んで適用
     * ListenPortManager初期化後に呼び出すことで、設定ファイル内の有効なプロキシが自動的に開始される
     * @return 設定ファイルが読み込まれた場合true
     */
    private fun loadSettingsFromJson(jsonPath: String?): Boolean {
        if (jsonPath?.isEmpty() ?: true) {
            return false
        }

        return try {
            Logging.log("設定ファイルを読み込みます: $jsonPath")
            val jsonBytes = Utils.readfile(jsonPath)
            val json = String(jsonBytes, Charsets.UTF_8)

            val configIO = ConfigIO()
            configIO.setOptions(json)

            Logging.log("設定ファイルを正常に読み込みました: $jsonPath")
            Logging.log("設定ファイル内の有効なプロキシは自動的に開始されます")
            true
        } catch (e: Exception) {
            Logging.err("設定ファイルの読み込みに失敗しました: ${e.message}", e)
            Logging.errWithStackTrace(e)
            false
        }
    }
}
