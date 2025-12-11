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
package packetproxy.gulp

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import packetproxy.cli.CLIModeHandler
import packetproxy.cli.EncodeModeHandler
import packetproxy.common.ConfigIO
import packetproxy.common.Utils
import packetproxy.util.Logging

object GulpTerminal {
  @JvmStatic
  @JvmOverloads
  fun run(settingJsonPath: String? = null) {
    // 設定ファイルを読み込む（ListenPortManager初期化後）
    loadSettingsFromJson(settingJsonPath)

    val terminal =
      try {
        TerminalBuilder.builder().system(true).build()
      } catch (e: Exception) {
        fallbackInput(e, "Terminal")
        return
      }

    // 主な利用用途としてscannerのEncodeを想定しているため、起動時のモードはEncodeModeが良い
    var currentHandler: CLIModeHandler = EncodeModeHandler
    val dynamicCompleter = DynamicCompleter(currentHandler)
    val reader =
      try {
        LineReaderBuilder.builder().terminal(terminal).completer(dynamicCompleter).build()
      } catch (e: Exception) {
        terminal.close()
        fallbackInput(e, "LineReader")
        return
      }

    println("=== CLI Mode ===")
    try {
      // exitされるまでコマンド入力に対応する
      // つづくコマンドを処理するべきmodeが変わった場合は補完内容なども切り替える
      while (true) {
        try {
          val line = reader.readLine(currentHandler.prompts)
          val parsed = CommandParser.parse(line) ?: continue
          when (parsed.cmd) {
            "" -> continue
            "exit" -> break

            else -> {
              currentHandler = currentHandler.handleCommand(parsed)
              dynamicCompleter.updateHandler(currentHandler)
            }
          }
        } catch (e: UserInterruptException) {
          // Ctrl + C: 継続、改行
          continue
        } catch (e: EndOfFileException) {
          // Ctrl + D: 終了
          println("${currentHandler.prompts}exit")
          break
        } catch (e: Exception) {
          Logging.errWithStackTrace(e)
          e.printStackTrace()
        }
      }
    } finally {
      terminal.close()
    }
  }

  // フォールバック用の簡易入力処理
  private fun fallbackInput(e: Exception, msg: String) {
    Logging.log("$msg の初期化に失敗しました。フォールバックモードに移行します: ${e.message}")
    println("=== Fallback CLI Mode ===")

    val scanner = java.util.Scanner(System.`in`)
    while (true) {
      try {
        print("> ")
        val line = scanner.nextLine()
        val parsed = CommandParser.parse(line) ?: continue
        when (parsed.cmd) {
          "" -> continue
          "exit" -> return

          "status" -> println("稼働中 (Port: 8080)")
          "help" -> println("使えるコマンド: exit, status, monitor")
          else -> println("不明なコマンド: $line")
        }
      } catch (e: Exception) {
        // Scannerのエラー（EOFなど）: 終了
        println("\n終了します。")
        return
      }
    }
  }

  /** JSON設定ファイルを読み込んで適用 ListenPortManager初期化後に呼び出すことで、設定ファイル内の有効なプロキシが自動的に開始される */
  private fun loadSettingsFromJson(jsonPath: String?) {
    if (jsonPath?.isEmpty() ?: true) return

    try {
      Logging.log("設定ファイルを読み込みます: $jsonPath")
      val jsonBytes = Utils.readfile(jsonPath)
      val json = String(jsonBytes, Charsets.UTF_8)

      val configIO = ConfigIO()
      configIO.setOptions(json)

      Logging.log("設定ファイルを正常に読み込みました: $jsonPath")
      Logging.log("設定ファイル内の有効なプロキシは自動的に開始されます")
    } catch (e: Exception) {
      Logging.err("設定ファイルの読み込みに失敗しました: ${e.message}", e)
      Logging.errWithStackTrace(e)
    }
  }
}
