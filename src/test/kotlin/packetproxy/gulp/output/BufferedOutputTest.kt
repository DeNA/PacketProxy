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
package packetproxy.gulp.output

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BufferedOutputTest {

  private lateinit var output: BufferedOutput

  @BeforeEach
  fun setUp() {
    output = BufferedOutput()
  }

  @Test
  fun printlnで改行付きで出力されること() {
    output.println("Hello")
    output.println("World")

    assertThat(output.getOutput()).isEqualTo("Hello\nWorld\n")
  }

  @Test
  fun printで改行なしで出力されること() {
    output.print("Hello")
    output.print("World")

    assertThat(output.getOutput()).isEqualTo("HelloWorld")
  }

  @Test
  fun printとprintlnを混在させられること() {
    output.print("Hello ")
    output.println("World")
    output.print("!")

    assertThat(output.getOutput()).isEqualTo("Hello World\n!")
  }

  @Test
  fun 空文字列のprintlnで改行のみ出力されること() {
    output.println()
    output.println()

    assertThat(output.getOutput()).isEqualTo("\n\n")
  }

  @Test
  fun clearでバッファがクリアされること() {
    output.println("Hello")
    output.clear()

    assertThat(output.getOutput()).isEmpty()
  }

  @Test
  fun styleがPlainStyleであること() {
    assertThat(output.style).isEqualTo(PlainStyle)
  }

  @Test
  fun styleの色コードが空文字列であること() {
    assertThat(output.style.red).isEmpty()
    assertThat(output.style.green).isEmpty()
    assertThat(output.style.reset).isEmpty()
  }
}
