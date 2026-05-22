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

import kotlinx.coroutines.Job
import packetproxy.cli.CLIModeHandler
import packetproxy.cli.EncodeModeHandler
import packetproxy.gulp.output.CommandOutput
import packetproxy.gulp.output.ConsoleOutput
import packetproxy.gulp.output.OutputStyle

/**
 * コマンド実行コンテキスト
 *
 * @param output 出力先（デフォルト: 標準出力）
 */
class CommandContext(val output: CommandOutput = ConsoleOutput) {
  var currentHandler: CLIModeHandler = EncodeModeHandler
  var executionJob: Job? = null

  fun cancelJob() {
    executionJob?.cancel()
  }

  // 便利メソッド: 出力
  fun println(text: String = "") = output.println(text)

  fun print(text: String) = output.print(text)

  // スタイル（色付け）へのショートカット
  val style: OutputStyle
    get() = output.style
}
