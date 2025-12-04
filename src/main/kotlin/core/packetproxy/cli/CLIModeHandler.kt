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

import org.jline.builtins.Completers.TreeCompleter

/**
 * CLIモードのハンドラーインターフェース
 * 各モード（encode/decode）で異なるコマンド処理と補完を提供
 */
interface CLIModeHandler {
    /**
     * モード名を取得
     */
    fun getModeName(): String

    /**
     * プロンプト文字列を取得
     */
    fun getPrompt(): String

    /**
     * TreeCompleterを生成
     */
    fun createCompleter(): TreeCompleter

    /**
     * コマンドを処理
     * @param cmd コマンド名
     * @param args コマンド引数
     * @return 処理が成功した場合true、コマンドがこのハンドラーで処理されなかった場合false
     */
    fun handleCommand(cmd: String, args: List<String>): Boolean

    /**
     * ヘルプメッセージを取得
     */
    fun getHelpMessage(): String
}

