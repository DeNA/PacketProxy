/*
 * Copyright 2026 DeNA Co., Ltd.
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
package packetproxy.util

/** テキスト中の一致の開始位置を昇順に持つ。重なる一致は数えない */
class SearchMatches private constructor(private val starts: IntArray, val wordLength: Int) {
  companion object {
    val EMPTY = SearchMatches(IntArray(0), 0)

    fun find(text: String, word: String): SearchMatches {
      if (text.isEmpty() || word.isEmpty()) return EMPTY
      var starts = IntArray(16)
      var count = 0
      var i = text.indexOf(word)
      while (i >= 0) {
        if (count == starts.size) starts = starts.copyOf(count * 2)
        starts[count++] = i
        i = text.indexOf(word, i + word.length)
      }
      return SearchMatches(starts.copyOf(count), word.length)
    }
  }

  val size: Int
    get() = starts.size

  fun start(index: Int): Int = starts[index]

  fun end(index: Int): Int = starts[index] + wordLength

  /** 開始位置が pos 以上の最初の一致。なければ size */
  private fun lowerBound(pos: Int): Int {
    var lo = 0
    var hi = starts.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (starts[mid] < pos) lo = mid + 1 else hi = mid
    }
    return lo
  }

  /** [from, to) と重なる一致の番号の範囲 */
  fun indicesOverlapping(from: Int, to: Int): IntRange {
    if (from >= to) return IntRange.EMPTY
    val first = lowerBound(from - wordLength + 1)
    val last = lowerBound(to) - 1
    return first..last
  }

  /** pos 以降で最初の一致。末尾を過ぎたら先頭に戻る。一致がなければ -1 */
  fun nextFrom(pos: Int): Int {
    if (starts.isEmpty()) return -1
    val i = lowerBound(pos)
    return if (i < starts.size) i else 0
  }

  /** pos より前で最後の一致。先頭を過ぎたら末尾に戻る。一致がなければ -1 */
  fun previousBefore(pos: Int): Int {
    if (starts.isEmpty()) return -1
    val i = lowerBound(pos) - 1
    return if (i >= 0) i else starts.size - 1
  }
}
