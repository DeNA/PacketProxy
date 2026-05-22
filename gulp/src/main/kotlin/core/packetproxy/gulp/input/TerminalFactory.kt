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
package packetproxy.gulp.input

import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import packetproxy.gulp.CommandContext
import packetproxy.gulp.DynamicCompleter

/** DefaultTerminalの起動を試みる 失敗時は代わりにFallbackTerminalを起動する */
object TerminalFactory {
  fun create(cmdCtx: CommandContext): LineSource {
    return try {
      val terminal = TerminalBuilder.builder().system(true).build()

      // コマンド実行中にSIGINTを受け取った際はコマンドの停止を行う
      terminal.handle(Terminal.Signal.INT) { _ -> cmdCtx.cancelJob() }

      val dynamicCompleter = DynamicCompleter(cmdCtx)
      val reader =
        LineReaderBuilder.builder().terminal(terminal).completer(dynamicCompleter).build()

      TerminalSource(cmdCtx, terminal, reader)
    } catch (e: Exception) {
      val scanner = java.util.Scanner(System.`in`)
      FallBackTerminalSource(cmdCtx, scanner)
    }
  }
}
