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

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import packetproxy.cli.CLIModeHandler
import packetproxy.cli.EncodeModeHandler
import packetproxy.gulp.input.ChainedSource
import packetproxy.gulp.input.LineSource
import packetproxy.gulp.input.ScriptSource

class SourceCommandTest {
  @TempDir lateinit var tempDir: Path

  @BeforeEach
  fun setUp() {
    // ChainedSourceの状態をクリア
    clearChainedSource()
  }

  @AfterEach
  fun tearDown() {
    // テスト後にChainedSourceの状態をクリア
    clearChainedSource()
  }

  private fun createMockTerminal(): LineSource {
    return object : LineSource() {
      override fun execOpen() {}

      override fun readLine(): String? = null

      override fun close() {}
    }
  }

  /** ChainedSourceの内部状態をクリアするヘルパーメソッド すべてのソースを読み取ってクリーンアップすることで状態をリセット */
  private fun clearChainedSource() {
    try {
      // すべてのソースを読み取ってクリーンアップ
      while (true) {
        val line = ChainedSource.readLine() ?: break
        // 読み取った行は無視（クリーンアップのため）
      }
    } catch (e: Exception) {
      // エラーが発生した場合は無視（クリーンアップのため）
    }
  }

  @Test
  fun 指定されたファイルを正しく読み取れること() {
    // テスト用のスクリプトファイルを作成
    val scriptFile = tempDir.resolve("test_script.pp").toFile()
    scriptFile.writeText(
      """
      |command1
      |command2
      |command3
      """
        .trimMargin()
    )

    val source = ScriptSource(scriptFile.absolutePath)
    source.open()

    val lines = mutableListOf<String?>()
    while (true) {
      val line = source.readLine() ?: break
      lines.add(line)
    }

    source.close()

    assertThat(lines).containsExactly("command1", "command2", "command3")
  }

  @Test
  fun 存在しないファイルの場合はnullを返すこと() {
    val nonExistentFile = tempDir.resolve("non_existent.pp").toFile()
    val source = ScriptSource(nonExistentFile.absolutePath)
    source.open()

    val line = source.readLine()

    source.close()

    assertThat(line).isNull()
  }

  @Test
  fun 指定されたファイルに記載された複数のコマンドを1つずつ実行できること() {
    // テスト用のスクリプトファイルを作成
    val scriptFile = tempDir.resolve("multi_command_script.pp").toFile()
    scriptFile.writeText(
      """
      |encode
      |decode
      |help
      """
        .trimMargin()
    )

    // モックのLineSourceを作成（ターミナルをシミュレート）
    val mockTerminal = createMockTerminal()

    val scriptSource = ScriptSource(scriptFile.absolutePath)

    ChainedSource.push(mockTerminal)
    ChainedSource.push(scriptSource)
    ChainedSource.open()

    // コマンドを1つずつ読み取って実行をシミュレート
    val commands = mutableListOf<String>()
    while (true) {
      val line = ChainedSource.readLine() ?: break
      val parsed = CommandParser.parse(line)
      if (parsed != null && parsed.cmd.isNotEmpty()) {
        commands.add(parsed.cmd)
      }
    }

    // リソースをクリーンアップ
    scriptSource.close()
    mockTerminal.close()

    assertThat(commands).containsExactly("encode", "decode", "help")
  }

  @Test
  suspend fun 指定されたファイル内に新たにsourceコマンドが記載されていた場合に正しく実行できること() {
    // ネストしたスクリプトファイルを作成
    val innerScriptFile = tempDir.resolve("inner_script.pp").toFile()
    innerScriptFile.writeText(
      """
      |inner_command1
      |inner_command2
      """
        .trimMargin()
    )

    val outerScriptFile = tempDir.resolve("outer_script.pp").toFile()
    outerScriptFile.writeText(
      """
      |outer_command1
      |source ${innerScriptFile.absolutePath}
      |outer_command2
      """
        .trimMargin()
    )

    // モックのLineSourceを作成（ターミナルをシミュレート）
    val mockTerminal = createMockTerminal()

    val outerScriptSource = ScriptSource(outerScriptFile.absolutePath)

    ChainedSource.push(mockTerminal)
    ChainedSource.push(outerScriptSource)
    ChainedSource.open()

    // CLIModeHandlerをシミュレートして、sourceコマンドを処理
    val handler: CLIModeHandler = EncodeModeHandler
    val commands = mutableListOf<String>()

    while (true) {
      val line = ChainedSource.readLine() ?: break
      val parsed = CommandParser.parse(line) ?: continue

      when (parsed.cmd) {
        "" -> continue
        ".",
        "source" -> {
          // sourceコマンドを検出したら、CLIModeHandlerのhandleCommandを呼び出して
          // 新しいScriptSourceをChainedSourceにプッシュ
          handler.handleCommand(parsed)
        }

        else -> {
          commands.add(parsed.cmd)
        }
      }
    }

    // リソースをクリーンアップ
    outerScriptSource.close()
    mockTerminal.close()

    // 外側のスクリプトのコマンドが最初に実行され、
    // sourceコマンドが検出され、内側のスクリプトが実行されることを確認
    assertThat(commands)
      .containsExactly("outer_command1", "inner_command1", "inner_command2", "outer_command2")
  }

  @Test
  suspend fun sourceした中でexitが実行されていれば全体でexitできること() {
    // ネストしたスクリプトファイルを作成
    val layer2ScriptFile = tempDir.resolve("layer_2.pp").toFile()
    layer2ScriptFile.writeText(
      """
      |layer_2_top
      |layer_2_bottom
      """
        .trimMargin()
    )

    val layer1ScriptFile = tempDir.resolve("layer_1.pp").toFile()
    layer1ScriptFile.writeText(
      """
      |layer_1_top
      |. ${layer2ScriptFile.absolutePath}
      |exit
      |layer_1_bottom
      """
        .trimMargin()
    )

    val layer0ScriptFile = tempDir.resolve("layer_0.pp").toFile()
    layer0ScriptFile.writeText(
      """
      |layer_0_top
      |source ${layer1ScriptFile.absolutePath}
      |layer_0_bottom
      """
        .trimMargin()
    )

    val layer0ScriptSource = ScriptSource(layer0ScriptFile.absolutePath)

    // モックのLineSourceを作成（ターミナルをシミュレート）
    val mockTerminal = createMockTerminal()

    ChainedSource.push(mockTerminal)
    ChainedSource.push(layer0ScriptSource)
    ChainedSource.open()

    // CLIModeHandlerをシミュレートして、sourceコマンドを処理
    val handler: CLIModeHandler = EncodeModeHandler
    val commands = mutableListOf<String>()

    while (true) {
      val line = ChainedSource.readLine() ?: break
      val parsed = CommandParser.parse(line) ?: continue

      when (parsed.cmd) {
        "" -> continue
        "exit" -> break

        ".",
        "source" -> {
          // sourceコマンドを検出したら、CLIModeHandlerのhandleCommandを呼び出して
          // 新しいScriptSourceをChainedSourceにプッシュ
          handler.handleCommand(parsed)
        }

        else -> {
          commands.add(parsed.cmd)
        }
      }
    }

    // リソースをクリーンアップ
    layer0ScriptSource.close()
    mockTerminal.close()

    // sourceコマンドで読み込んだスクリプトにexitが含まれていれば呼び出し元も含めてexitすることを確認
    assertThat(commands)
      .containsExactly("layer_0_top", "layer_1_top", "layer_2_top", "layer_2_bottom")
  }

  @Test
  fun 空のファイルの場合は何も読み取れないこと() {
    val emptyFile = tempDir.resolve("empty_script.pp").toFile()
    emptyFile.createNewFile()

    val source = ScriptSource(emptyFile.absolutePath)
    source.open()

    val line = source.readLine()

    source.close()

    assertThat(line).isNull()
  }

  @Test
  fun コメントが含まれるファイルを正しく読み取れること() {
    val scriptFile = tempDir.resolve("comment_script.pp").toFile()
    scriptFile.writeText(
      """
      |command1 # comment
      |# co mm  en t!
      |command2
      |# source ${scriptFile.absolutePath}
      |command3
      """
        .trimMargin()
    )

    val source = ScriptSource(scriptFile.absolutePath)
    source.open()

    val lines = mutableListOf<String?>()
    while (true) {
      val line = source.readLine() ?: break
      lines.add(line)
    }

    source.close()

    // ファイルから読み取られた行（コメント処理前）
    assertThat(lines)
      .containsExactly(
        "command1 # comment",
        "# co mm  en t!",
        "command2",
        "# source ${scriptFile.absolutePath}",
        "command3",
      )

    // CommandParserでパースすると、コメントが削除されることを確認
    val parsedCommands = lines.mapNotNull { CommandParser.parse(it ?: "") }
    assertThat(parsedCommands.map { it.cmd }).containsExactly("command1", "command2", "command3")
  }

  @Test
  fun 非asciiのコメントが含まれるファイルを正しく読み取れること() {
    val scriptFile = tempDir.resolve("comment_script.pp").toFile()
    scriptFile.writeText(
      """
      |command1 # コメント
      | # コメント
      |command2
      |#これは、　コメントです！
      |command3
      """
        .trimMargin()
    )

    val source = ScriptSource(scriptFile.absolutePath)
    source.open()

    val lines = mutableListOf<String?>()
    while (true) {
      val line = source.readLine() ?: break
      lines.add(line)
    }

    source.close()

    // ファイルから読み取られた行（コメント処理前）
    assertThat(lines)
      .containsExactly("command1 # コメント", " # コメント", "command2", "#これは、　コメントです！", "command3")

    // CommandParserでパースすると、コメントが削除されることを確認
    val parsedCommands = lines.mapNotNull { CommandParser.parse(it ?: "") }
    assertThat(parsedCommands.map { it.cmd }).containsExactly("command1", "command2", "command3")
  }
}
