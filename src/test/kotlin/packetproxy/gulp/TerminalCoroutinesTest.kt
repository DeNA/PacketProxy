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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import packetproxy.gulp.input.ChainedSource
import packetproxy.gulp.input.LineSource

class TerminalCoroutinesTest {
  @TempDir lateinit var tempDir: Path

  @BeforeEach
  fun setUp() {
    // ChainedSourceの状態をクリア
  }

  @AfterEach
  fun tearDown() {
    // テスト後にChainedSourceの状態をクリア
  }

  private fun createMockTerminal(): LineSource {
    return object : LineSource() {
      override fun execOpen() {}

      override fun readLine(): String? = null

      override fun close() {}
    }
  }

  @Test
  fun コマンド実行中にcancelJobを実行することでコマンドの中断ができること() = runBlocking {
    val mockTerminal = createMockTerminal()

    ChainedSource.push(mockTerminal)
    ChainedSource.open()

    val cmdCtx = CommandContext()

    // 長時間実行されるコマンドをシミュレート
    var commandStarted = false
    var commandCancelled = false

    cmdCtx.executionJob = launch {
      try {
        commandStarted = true
        delay(5000)

        // ここには到達しないはず
        assertThat(false).withFailMessage("コマンドが中断されずに完了してしまった").isTrue()
      } catch (e: CancellationException) {
        commandCancelled = true
      }
    }

    // コマンドが開始された後に中断を実行する
    delay(100) // コマンドが開始されるまで少し待機
    assertThat(commandStarted).isTrue()
    assertThat(cmdCtx.executionJob?.isActive).isTrue()

    cmdCtx.cancelJob()
    cmdCtx.executionJob?.join()

    // コマンドが中断されたことを確認
    assertThat(commandCancelled).isTrue()
    assertThat(cmdCtx.executionJob?.isCancelled).isTrue()
    assertThat(cmdCtx.executionJob?.isCompleted).isTrue()

    mockTerminal.close()
  }
}
