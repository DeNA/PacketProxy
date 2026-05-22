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
import org.junit.jupiter.api.Test

class OutputStyleTest {

  @Test
  fun AnsiStyleのresetがANSIエスケープシーケンスであること() {
    assertThat(AnsiStyle.reset).isEqualTo("\u001B[0m")
  }

  @Test
  fun AnsiStyleの各色がANSIエスケープシーケンスであること() {
    assertThat(AnsiStyle.red).isEqualTo("\u001B[31m")
    assertThat(AnsiStyle.green).isEqualTo("\u001B[32m")
    assertThat(AnsiStyle.yellow).isEqualTo("\u001B[33m")
    assertThat(AnsiStyle.blue).isEqualTo("\u001B[34m")
    assertThat(AnsiStyle.cyan).isEqualTo("\u001B[36m")
  }

  @Test
  fun PlainStyleの全ての値が空文字列であること() {
    assertThat(PlainStyle.reset).isEmpty()
    assertThat(PlainStyle.bold).isEmpty()
    assertThat(PlainStyle.red).isEmpty()
    assertThat(PlainStyle.green).isEmpty()
    assertThat(PlainStyle.yellow).isEmpty()
    assertThat(PlainStyle.blue).isEmpty()
    assertThat(PlainStyle.cyan).isEmpty()
  }

  @Test
  fun AnsiStyleのcoloredメソッドが正しく色付けすること() {
    val result = AnsiStyle.colored("Hello", AnsiStyle.red)

    assertThat(result).isEqualTo("\u001B[31mHello\u001B[0m")
  }

  @Test
  fun PlainStyleのcoloredメソッドが色なしで返すこと() {
    val result = PlainStyle.colored("Hello", PlainStyle.red)

    assertThat(result).isEqualTo("Hello")
  }

  @Test
  fun AnsiStyleのsuccessメソッドが緑色で返すこと() {
    val result = AnsiStyle.success("OK")

    assertThat(result).isEqualTo("\u001B[32mOK\u001B[0m")
  }

  @Test
  fun AnsiStyleのerrorメソッドが赤色で返すこと() {
    val result = AnsiStyle.error("Error")

    assertThat(result).isEqualTo("\u001B[31mError\u001B[0m")
  }

  @Test
  fun AnsiStyleのwarningメソッドが黄色で返すこと() {
    val result = AnsiStyle.warning("Warning")

    assertThat(result).isEqualTo("\u001B[33mWarning\u001B[0m")
  }

  @Test
  fun AnsiStyleのinfoメソッドがシアンで返すこと() {
    val result = AnsiStyle.info("Info")

    assertThat(result).isEqualTo("\u001B[36mInfo\u001B[0m")
  }

  @Test
  fun PlainStyleの便利メソッドが全て色なしで返すこと() {
    assertThat(PlainStyle.success("OK")).isEqualTo("OK")
    assertThat(PlainStyle.error("Error")).isEqualTo("Error")
    assertThat(PlainStyle.warning("Warning")).isEqualTo("Warning")
    assertThat(PlainStyle.info("Info")).isEqualTo("Info")
  }
}
