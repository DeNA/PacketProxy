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
import org.jline.builtins.Completers.TreeCompleter
import org.jline.builtins.Completers.TreeCompleter.node
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.TerminalBuilder
import packetproxy.util.Logging
import kotlin.concurrent.thread

object CLIMode {
    @JvmStatic
    fun run() {
        thread {
            startProxyServer()
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

        // readerのbuildを試行
        val reader = try {
            val commandCompleter = TreeCompleter(
                node("exit"),
                node("echo"),
                node("help"),
                node("status"),

                node("set", node(StringsCompleter("encoder", "proxy"))),
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
            val ps1 = Ansi.ansi()
                .fg(CYAN).a("> ").reset()
                .toString()

            while (true) {
                try {
                    val line = reader.readLine(ps1).trim()
                    if (line.isEmpty()) continue

                    val (cmd, args) = CommandParser.parse(line)
                    when (cmd) {
                        "echo" -> println("Echo: ${args.joinToString(", ")}")
                        "exit" -> break
                        "help" -> println("echo | exit | status | set")
                        "status" -> println("稼働中 (Port: 8080)")

                        "set" -> {
                            if (args.size <= 1) {
                                println("encoder | proxy")
                            } else {
                                println("${args[0]}: ${args[1]}")
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

    private fun startProxyServer() {
        var i = 0
        Logging.log("Proxy Server listening on port 8080...")
        while (true) {
            Logging.log("hi: %d", i++)
            Thread.sleep(10000)
        }
    }
}
