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

/**
 * バッファへの出力実装（テスト・内部連携用）
 *
 * 出力内容を蓄積し、後から取得可能にする。 色付けは無効（PlainStyle）のため、ANSIエスケープシーケンスは含まれない。
 */
class BufferedOutput : CommandOutput {
  override val style: OutputStyle = PlainStyle

  private val buffer = StringBuilder()

  override fun println(text: String) {
    buffer.appendLine(text)
  }

  override fun print(text: String) {
    buffer.append(text)
  }

  override fun getOutput(): String = buffer.toString()

  override fun clear() {
    buffer.clear()
  }
}
