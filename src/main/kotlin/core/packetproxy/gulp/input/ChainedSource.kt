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
package packetproxy.gulp.input

/** コマンド入力を受け付けるインスタンスをスタックで管理する */
object ChainedSource {
  // 読み取るソースのスタック
  private val sources = ArrayDeque<LineSource>()

  // 最初のLineSourceのみpopでopenできないため個別に実行する
  fun open() {
    sources.last().open()
  }

  fun push(source: LineSource) {
    sources.addLast(source)
  }

  // ソースのスタックが存在しない場合のみnullを返す
  fun readLine(): String? {
    while (sources.isNotEmpty()) {
      val line = sources.last().readLine()
      if (line != null) return line

      pop()
    }

    return null
  }

  /** sourcesの末尾のpopを実行する pop直前にリソースの解放と次の末尾のリソースの準備を実行する */
  private fun pop() {
    sources.last().close()
    sources.removeLast()
    sources.last().open()
  }
}
