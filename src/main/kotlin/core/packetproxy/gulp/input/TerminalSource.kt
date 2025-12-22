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

import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import packetproxy.gulp.CommandContext

/** デフォルトのターミナル キー移動やコマンド補完に対応している */
class TerminalSource(
  private val cmdCtx: CommandContext,
  private val terminal: Terminal,
  private val reader: LineReader,
) : LineSource {
  override fun open() {
    println("=== CLI Mode ===")
  }

  override fun readLine(): String {
    return reader.readLine(cmdCtx.currentHandler.prompts)
  }

  override fun close() {
    terminal.close()
  }
}
