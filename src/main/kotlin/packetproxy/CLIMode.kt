package packetproxy

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
                node("help"),
                node("status"),
                node("show", node(StringsCompleter("logs", "config"))),
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

        // 対話ループ
        while (true) {
            try {
                val command = reader.readLine("> ").trim()

                when (command) {
                    "exit" -> {
                        terminal.close()
                        return
                    }

                    "status" -> println("稼働中 (Port: 8080)")
                    "help" -> println("使えるコマンド: exit, status, show")
                    "show logs" -> println("s logs")
                    "show config" -> println("s config")
                    "" -> continue
                    else -> println("不明なコマンド: $command")
                }
            } catch (e: UserInterruptException) {
                // Ctrl + C: アプリケーション継続、改行
            } catch (e: EndOfFileException) {
                // Ctrl + D: アプリケーションを終了
                println("> exit")
                terminal.close()
                return
            } catch (e: Exception) {
                // その他の例外: ログを出力して対話を続ける
                Logging.errWithStackTrace(e)
                e.printStackTrace()
            }
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
            Thread.sleep(5000)
        }
    }
}
