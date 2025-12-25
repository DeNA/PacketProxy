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

import packetproxy.gulp.CommandContext

/**
 * デフォルトのターミナルの起動に失敗した際のフォールバック用ターミナル 最低限の機能のみを提供 Ctrl
 * Cによる改行やコマンドの中断が不可能だが、多くの場合はデフォルトのターミナルを使用可能だと思われるため許容
 */
class FallBackTerminalSource(
  private val cmdCtx: CommandContext,
  private val scanner: java.util.Scanner,
) : LineSource() {
  override fun execOpen() {
    println("=== Fallback CLI Mode ===")
  }

  override fun readLine(): String {
    try {
      print(cmdCtx.currentHandler.prompts)
      return scanner.nextLine()
    } catch (e: NoSuchElementException) {
      // FallbackTerminalでCtrl+Dが押下された場合に投げられる例外
      // DefaultTerminalでCtrl+Dが押下された場合と同じ例外に変換している
      println() // 表記をDefaultTerminalと揃える
      throw org.jline.reader.EndOfFileException("EOF from Standard Input")
    }
  }

  override fun close() {
    scanner.close()
  }
}
