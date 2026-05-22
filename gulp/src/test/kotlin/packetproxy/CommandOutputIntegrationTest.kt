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

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import packetproxy.cli.DecodeModeHandler
import packetproxy.cli.EncodeModeHandler
import packetproxy.gulp.output.BufferedOutput

class CommandOutputIntegrationTest {

  private lateinit var output: BufferedOutput
  private lateinit var ctx: CommandContext

  @BeforeEach
  fun setUp() {
    output = BufferedOutput()
    ctx = CommandContext(output)
  }

  @Test
  fun EncodeModeでstatusコマンドがモード名を出力すること() = runBlocking {
    ctx.currentHandler = EncodeModeHandler
    val parsed = CommandParser.parse("status")!!

    ctx.currentHandler.handleCommand(parsed, ctx)

    assertThat(output.getOutput()).contains("Encode Mode")
  }

  @Test
  fun DecodeModeでstatusコマンドがモード名を出力すること() = runBlocking {
    ctx.currentHandler = DecodeModeHandler
    val parsed = CommandParser.parse("status")!!

    ctx.currentHandler.handleCommand(parsed, ctx)

    assertThat(output.getOutput()).contains("Decode Mode")
  }

  @Test
  fun helpコマンドがヘルプメッセージを出力すること() = runBlocking {
    ctx.currentHandler = EncodeModeHandler
    val parsed = CommandParser.parse("help")!!

    ctx.currentHandler.handleCommand(parsed, ctx)

    val result = output.getOutput()
    assertThat(result).contains("共通コマンド")
    assertThat(result).contains("exit")
    assertThat(result).contains("help")
    assertThat(result).contains("log")
  }

  @Test
  fun 未定義コマンドでエラーメッセージが出力されること() = runBlocking {
    ctx.currentHandler = EncodeModeHandler
    val parsed = CommandParser.parse("undefined_command_xyz")!!

    ctx.currentHandler.handleCommand(parsed, ctx)

    assertThat(output.getOutput()).contains("command not defined")
  }

  @Test
  fun 出力にANSIエスケープシーケンスが含まれないこと() = runBlocking {
    ctx.currentHandler = EncodeModeHandler
    val parsed = CommandParser.parse("status")!!

    ctx.currentHandler.handleCommand(parsed, ctx)

    val result = output.getOutput()
    // ANSIエスケープシーケンス（\u001B[）が含まれていないことを確認
    assertThat(result).doesNotContain("\u001B[")
  }

  @Test
  fun clearで出力がリセットされること() = runBlocking {
    ctx.currentHandler = EncodeModeHandler
    val parsed = CommandParser.parse("status")!!

    ctx.currentHandler.handleCommand(parsed, ctx)
    assertThat(output.getOutput()).isNotEmpty()

    output.clear()
    assertThat(output.getOutput()).isEmpty()
  }

  @Test
  fun styleを使用して色付きテキストを生成できること() {
    // ConsoleOutputではANSIコード付き、BufferedOutputでは色なし
    val successText = ctx.style.success("OK")
    val errorText = ctx.style.error("Error")

    // BufferedOutputなので色コードなし
    assertThat(successText).isEqualTo("OK")
    assertThat(errorText).isEqualTo("Error")
  }
}
