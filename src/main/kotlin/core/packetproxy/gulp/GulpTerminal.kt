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

import kotlinx.coroutines.*
import org.jline.reader.EndOfFileException
import org.jline.reader.UserInterruptException
import packetproxy.cli.DecodeModeHandler
import packetproxy.cli.EncodeModeHandler
import packetproxy.common.ConfigIO
import packetproxy.common.Utils
import packetproxy.gulp.input.ChainedSource
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

    runBlocking {
      while (isActive) {
        /** コマンド入力受付。Ctrl+C: continue 改行して次の入力受付を開始する Ctrl+D: break Terminalを閉じる */
        val line =
          try {
            withContext(Dispatchers.IO) { ChainedSource.readLine() } ?: break
          } catch (e: Exception) {
            when (e) {
              is UserInterruptException -> {} // Ctrl+C
              is EndOfFileException -> {
                println("${cmdCtx.currentHandler.prompts}exit")
                break
              } // Ctrl+D
              else -> Logging.errWithStackTrace(e)
            }
            continue
          }

        val parsed = CommandParser.parse(line) ?: continue

        /** コマンド実行。実行用の新しいコルーチンをlaunchしJobとして保持する。Ctrl+C: コマンドの実行を中断する */
        when (parsed.cmd) {
          "" -> continue
          "exit" -> break

          "s",
          "switch" -> cmdCtx.currentHandler = cmdCtx.currentHandler.getOppositeMode()

          "e",
          "encode" -> cmdCtx.currentHandler = EncodeModeHandler

          "d",
          "decode" -> cmdCtx.currentHandler = DecodeModeHandler

          else -> {
            cmdCtx.executionJob = launch {
              try {
                cmdCtx.currentHandler.handleCommand(parsed)
              } catch (e: Exception) {
                when (e) {
                  is CancellationException -> println() // Ctrl+Cによってcancel()が実行された結果throwされるもの
                  else -> Logging.errWithStackTrace(e)
                }
              }
            }

            cmdCtx.executionJob?.join()
          }
        }
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
