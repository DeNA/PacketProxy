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

import core.packetproxy.gulp.command.LogCommand
import org.jline.builtins.Completers.TreeCompleter
import org.jline.builtins.Completers.TreeCompleter.node
import packetproxy.common.I18nString
import packetproxy.gulp.ParsedCommand
import packetproxy.gulp.input.ChainedSource
import packetproxy.gulp.input.ScriptSource

/** CLIモードのハンドラーインターフェース 各モード（encode/decode）で異なるコマンド処理と補完を提供 */
abstract class CLIModeHandler {
  /** tab補完内容の設定 */
  val completer: TreeCompleter by lazy {
    val mergedNodes =
      listOf(
        node("exit"),
        node("switch"),
        node("decode"),
        node("encode"),
        node("log"),
        node("source"),
        node("help"),
      ) + extensionNodes()
    TreeCompleter(*mergedNodes.toTypedArray())
  }

  protected abstract fun extensionNodes(): List<TreeCompleter.Node>

  val prompts: String by lazy { getPrompt() }

  protected abstract fun getPrompt(): String

  /**
   * コマンドを処理
   *
   * @param parsed コマンド
   */
  suspend fun handleCommand(parsed: ParsedCommand) {
    when (parsed.cmd) {
      "help" -> println(getHelpMessage())

      ".",
      "source" -> ChainedSource.push(ScriptSource(parsed.args.firstOrNull() ?: ""))

      "l",
      "log" -> LogCommand(parsed)

      else -> extensionCommand(parsed)
    }
  }

  protected fun commandNotDefined(parsed: ParsedCommand) {
    println(I18nString.get("command not defined: %s", parsed.raw))
  }

  abstract fun getOppositeMode(): CLIModeHandler

  protected abstract fun extensionCommand(parsed: ParsedCommand)

  fun getHelpMessage(): String {
    return """共通コマンド：
  exit                     - 終了
  help                     - ヘルプ
  l, log                   - ログ継続出力
  s, switch                - Mode切り替え

専用コマンド：""" +
      extensionHelpMessage()
  }

  protected abstract fun extensionHelpMessage(): String
}
