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

        try {
            val commandCompleter = TreeCompleter(
                node("exit"),
                node("help"),
                node("status"),

                node("show", node(StringsCompleter("logs", "config"))),
            )

            val terminal = TerminalBuilder.builder()
                .system(true)
                .build()

            val reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(commandCompleter)
                .build()

            println("=== CLI Mode ===")

            while (true) {
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
            }

        } catch (e: UserInterruptException) {
            // Ctrl + C
            println("\n中断されました。終了します。")
        } catch (e: EndOfFileException) {
            // Ctrl + D
            println("\n終了します。")
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            fallbackInput()
        }
    }

    // フォールバック用の簡易入力処理
    private fun fallbackInput() {
        println("=== Fallback CLI Mode ===")

        val scanner = java.util.Scanner(System.`in`)
        while (true) {
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
