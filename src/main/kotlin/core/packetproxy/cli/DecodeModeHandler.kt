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

/**
 * Decode Modeのハンドラー
 */
class DecodeModeHandler : CLIModeHandler {
    override fun getModeName(): String = "decode"

    override fun getPrompt(): String {
        return Ansi.ansi()
            .fg(CYAN).a("D-B   > ").reset()
            .toString()
    }

    override fun createCompleter(): TreeCompleter {
        return TreeCompleter(
            node("d", "decode"),
            node("e", "encode"),
            node("exit"),
            node("echo"),
            node("help"),
            node("status"),
            // Decode mode固有のコマンドを追加可能
            node("decode", node("packet")),
        )
    }

    override fun handleCommand(cmd: String, args: List<String>): Boolean {
        return when (cmd) {
            "d", "decode" -> {
                true
            }

            "e", "encode" -> {
                // mode切り替えはCLIModeで処理
                false
            }

            "echo" -> {
                println("Echo (Decode Mode): ${args.joinToString(", ")}")
                true
            }

            "exit" -> {
                // exitはCLIModeで処理
                false
            }

            "help" -> {
                println(getHelpMessage())
                true
            }

            "status" -> {
                println("稼働中 (Port: 8080) [Decode Mode]")
                true
            }

            else -> false
        }
    }

    override fun getHelpMessage(): String {
        return """
利用可能なコマンド (Decode Mode):
  echo <text>              - テキストをエコー
  exit                     - 終了
  status                   - ステータス表示
  d, decode               - Decode Modeに切り替え（現在のモード）
  e, encode               - Encode Modeに切り替え
  decode packet <id>      - パケットをデコード（未実装）
        """.trimIndent()
    }
}

