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
import packetproxy.gulp.ParsedCommand

/** Decode Modeのハンドラー */
object DecodeModeHandler : CLIModeHandler() {
  override fun getPrompt(): String {
    return Ansi.ansi().fg(CYAN).a("D-B   > ").reset().toString()
  }

  override fun extensionNodes(): List<TreeCompleter.Node> {
    return listOf(node("status"))
  }

  override fun getOppositeMode(): CLIModeHandler {
    return EncodeModeHandler
  }

  override fun extensionCommand(parsed: ParsedCommand) {
    when (parsed.cmd) {
      "status" -> println("Decode Mode !")

      else -> commandNotDefined(parsed)
    }
  }

  override fun extensionHelpMessage(): String {
    return """
  e, encode,     - Encode Modeに切り替え
"""
  }
}
