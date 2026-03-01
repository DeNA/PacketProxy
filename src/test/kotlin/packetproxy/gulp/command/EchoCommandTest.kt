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
package packetproxy.gulp.command

import core.packetproxy.gulp.command.EchoCommand
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import packetproxy.gulp.CommandContext
import packetproxy.gulp.CommandParser
import packetproxy.gulp.output.BufferedOutput

/**
 * EchoCommandのテスト
 *
 * 内部連携のユースケースを検証: BufferedOutputを使用することで標準出力を汚さずに結果を取得できる
 */
class EchoCommandTest {
  private lateinit var output: BufferedOutput
  private lateinit var ctx: CommandContext

  @BeforeEach
  fun setUp() {
    output = BufferedOutput()
    ctx = CommandContext(output)
  }

  @Test
  fun 単一の引数を出力できること() = runBlocking {
    val parsed = CommandParser.parse("echo Hello")!!

    EchoCommand(parsed, ctx)

    assertThat(output.getOutput()).isEqualTo("Hello\n")
  }

  @Test
  fun 複数の引数をスペース区切りで出力できること() = runBlocking {
    val parsed = CommandParser.parse("echo Hello World")!!

    EchoCommand(parsed, ctx)

    assertThat(output.getOutput()).isEqualTo("Hello World\n")
  }

  @Test
  fun 引数なしの場合は空行が出力されること() = runBlocking {
    val parsed = CommandParser.parse("echo")!!

    EchoCommand(parsed, ctx)

    assertThat(output.getOutput()).isEqualTo("\n")
  }

  @Test
  fun 多数の引数を連結して出力できること() = runBlocking {
    val parsed = CommandParser.parse("echo foo bar baz qux")!!

    EchoCommand(parsed, ctx)

    assertThat(output.getOutput()).isEqualTo("foo bar baz qux\n")
  }

  @Test
  fun 複数回のコマンド実行結果を蓄積できること() = runBlocking {
    val parsed1 = CommandParser.parse("echo first")!!
    val parsed2 = CommandParser.parse("echo second")!!
    val parsed3 = CommandParser.parse("echo third")!!

    EchoCommand(parsed1, ctx)
    EchoCommand(parsed2, ctx)
    EchoCommand(parsed3, ctx)

    assertThat(output.getOutput()).isEqualTo("first\nsecond\nthird\n")
  }

  @Test
  fun clearで出力をリセットしてから再度取得できること() = runBlocking {
    val parsed1 = CommandParser.parse("echo before clear")!!
    EchoCommand(parsed1, ctx)

    output.clear()

    val parsed2 = CommandParser.parse("echo after clear")!!
    EchoCommand(parsed2, ctx)

    assertThat(output.getOutput()).isEqualTo("after clear\n")
  }

  @Test
  fun 出力にANSIエスケープシーケンスが含まれないこと() = runBlocking {
    val parsed = CommandParser.parse("echo test message")!!

    EchoCommand(parsed, ctx)

    val result = output.getOutput()
    // BufferedOutputはPlainStyleを使用するため、ANSIコードは含まれない
    assertThat(result).doesNotContain("\u001B[")
  }

  // 内部連携: MCPサーバとの連携など
  @Test
  fun 内部連携で標準出力を汚さずに結果を取得できること() = runBlocking {
    // BufferedOutputを使用した内部連携のデモ
    val internalOutput = BufferedOutput()
    val internalCtx = CommandContext(internalOutput)

    // コマンドを実行
    val parsed = CommandParser.parse("echo internal message")!!
    EchoCommand(parsed, internalCtx)

    // 結果をKotlinコードで取得（標準出力には出力されない）
    val result = internalOutput.getOutput().trim()

    // 取得した結果を検証
    assertThat(result).isEqualTo("internal message")

    // 結果を別の処理に渡す例
    val processedResult = result.uppercase()
    assertThat(processedResult).isEqualTo("INTERNAL MESSAGE")
  }
}
