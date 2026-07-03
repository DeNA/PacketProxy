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
package packetproxy.gui

import java.awt.Color

class MarkedOriginalRowHighlight {
  companion object {
    internal val DIFF_ORIGINAL_ROW_COLOR = Color(0xb0, 0xb0, 0xb0)
  }

  private data class MarkedRow(val packetId: Int, val savedRowColor: Color?)

  private var markedRow: MarkedRow? = null

  val hasMarkedOriginal: Boolean
    get() = markedRow != null

  fun markCurrentRowAsOriginal() {
    markedRow = captureMarkedRow()
    GUIHistory.getInstance().addCustomColoringToCursorPos(DIFF_ORIGINAL_ROW_COLOR)
  }

  fun restoreMarkedRowAndClear() {
    val marked = markedRow ?: return
    restoreRowColor(marked)
    markedRow = null
  }

  private fun captureMarkedRow(): MarkedRow {
    val history = GUIHistory.getInstance()
    val savedRowColor =
      if (history.containsColor()) {
        history.color
      } else {
        null
      }
    return MarkedRow(history.selectedPacketId, savedRowColor)
  }

  private fun restoreRowColor(marked: MarkedRow) {
    val history = GUIHistory.getInstance()
    if (marked.savedRowColor != null) {
      history.addCustomColoring(marked.packetId, marked.savedRowColor)
      return
    }
    history.removeCustomColoring(marked.packetId)
  }
}
