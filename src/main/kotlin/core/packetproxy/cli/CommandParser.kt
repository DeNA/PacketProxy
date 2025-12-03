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

object CommandParser {
    // 正規表現の解説:
    // [^\\s"]+  -> クォートでもスペースでもない文字の塊（普通の単語）
    // "([^"]*)" -> ダブルクォートで囲まれた中身（スペースを含める）
    private val regex = Regex("""[^\s"]+|"([^"]*)"""")

    fun parse(line: String): Pair<String, List<String>> {
        // マッチした部分を全部リストにする
        val tokens = regex.findAll(line).map { matchResult ->
            // クォートの中身(groupValues[1])があればそれを、なければ全体(groupValues[0])を使う
            matchResult.groupValues[1].ifEmpty { matchResult.groupValues[0] }
        }.toList()

        if (tokens.isEmpty()) {
            return Pair("", emptyList())
        }

        // 先頭をコマンド、残りを引数リストとして返す
        val command = tokens[0]
        val args = tokens.drop(1)

        return Pair(command, args)
    }
}