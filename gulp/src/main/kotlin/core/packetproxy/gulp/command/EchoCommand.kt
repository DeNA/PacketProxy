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
package core.packetproxy.gulp.command

import packetproxy.gulp.CommandContext
import packetproxy.gulp.ParsedCommand

/**
 * echoコマンド: 引数をそのまま出力する
 *
 * 使用例:
 * - echo Hello World -> "Hello World"
 * - echo foo bar baz -> "foo bar baz"
 *
 * 内部連携の際はBufferedOutputを使用することで、 標準出力を汚さずに結果を取得できる。
 */
object EchoCommand : Command {
  override suspend fun invoke(parsed: ParsedCommand, ctx: CommandContext) {
    val message = parsed.args.joinToString(" ")
    ctx.println(message)
  }
}
