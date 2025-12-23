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

import kotlin.math.abs
import org.jline.reader.EndOfFileException
import org.jline.reader.UserInterruptException
import packetproxy.common.ConfigIO
import packetproxy.common.Utils
import packetproxy.gulp.input.ChainedSource
import packetproxy.gulp.input.LineSource
import packetproxy.gulp.input.ScriptSource
import packetproxy.gulp.input.TerminalFactory
import packetproxy.util.Logging

object GulpTerminal {
  @JvmStatic
  fun run(settingJsonPath: String?, scriptFilePath: String) {
    // 設定ファイルを読み込む（ListenPortManager初期化後）
    loadSettingsFromJson(settingJsonPath)

    val cmdCtx = CommandContext()
    val terminal = TerminalFactory.create(cmdCtx)

    ChainedSource.push(terminal)
    ChainedSource.push(ScriptSource(scriptFilePath))
    ChainedSource.open()

    // exitされるまでコマンド入力に対応する
    // つづくコマンドを処理するべきmodeが変わった場合は補完内容なども切り替える
    while (true) {
      try {
        val line = ChainedSource.readLine() ?: break
        val parsed = CommandParser.parse(line) ?: continue

        if (parsed.raw.startsWith("#")) continue
        when (parsed.cmd) {
          "" -> continue
          "exit" -> break

          "l",
          "log" -> handleLogCommand(terminal, parsed.args)

          else -> cmdCtx.currentHandler = cmdCtx.currentHandler.handleCommand(parsed)
        }
      } catch (e: UserInterruptException) {
        // Ctrl + C: 継続、改行
        continue
      } catch (e: EndOfFileException) {
        // Ctrl + D: 終了
        println("${cmdCtx.currentHandler.prompts}exit")
        break
      } catch (e: Exception) {
        Logging.errWithStackTrace(e)
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

  /** readerを渡したいのでCLIModeHandlerでは処理しない */
  private fun handleLogCommand(terminal: LineSource, args: List<String>) {
    val lineCount = abs(args.firstOrNull()?.toIntOrNull() ?: 0)

    // 引数で数値が指定された場合は末尾からその行数分出力して終了
    if (lineCount != 0) {
      Logging.printLog(lineCount)
      return
    }

    // 引数に数値が指定されなかった場合は継続出力を行う(Ctrl + C/D で終了)
    try {
      // 最初に末尾30行分出力してしまう
      Logging.printLog(30)
      Logging.startTailLog()
      while (true) terminal.readLine()
    } catch (e: Exception) {
      when (e) {
        is UserInterruptException,
        is EndOfFileException -> {}

        else -> Logging.errWithStackTrace(e)
      }
    } finally {
      Logging.stopTailLog()
    }
  }
}
