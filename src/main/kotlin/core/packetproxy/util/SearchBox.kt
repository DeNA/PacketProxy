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

import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent
import packetproxy.common.FontManager
import packetproxy.common.Range
import packetproxy.util.Logging.errWithStackTrace

/**
 * テキストの検索と強調表示。
 *
 * 色付けは Document の文字属性を書き換えずに Highlighter で行う。Highlighter は描画時に見えている範囲だけを塗るので、巨大なテキストでも UI
 * スレッドを長く止めない。
 */
class SearchBox @Throws(Exception::class) constructor() : JPanel() {
  companion object {
    private const val MAX_TEXT_LENGTH_FOR_HIGHLIGHTING = 1_000_000

    /**
     * 一致を色付けする件数の上限。上限を超えた分は件数だけ数える。
     *
     * 長い 1 行に多くのハイライトが乗ると、描画が極端に遅くなるので少なく抑える。10 万文字の 1 行に 2400 件で、1 回の描画に 10 秒以上かかっていた。
     */
    internal const val MAX_HIGHLIGHTED_MATCHES = 200

    private val MATCH_PAINTER = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
    private val CURRENT_PAINTER = DefaultHighlighter.DefaultHighlightPainter(Color.MAGENTA)
    private val EMPHASIS_PAINTER =
      DefaultHighlighter.DefaultHighlightPainter(Color(0xB0, 0xF0, 0xB0))

    private fun isOwnPainter(painter: Highlighter.HighlightPainter?): Boolean =
      painter === MATCH_PAINTER || painter === CURRENT_PAINTER || painter === EMPHASIS_PAINTER

    /** 選択範囲などのハイライトは残し、このクラスが付けたものだけを消す */
    private fun removeOwnHighlights(text: JTextComponent) {
      val highlighter = text.highlighter
      for (h in highlighter.highlights) {
        if (isOwnPainter(h.painter)) highlighter.removeHighlight(h)
      }
    }
  }

  private var baseText: JTextComponent? = null
  private var emphasisArea: Range? = null
  private val searchText = JTextField()
  private val searchCount = JLabel("Not found")

  init {
    searchText.font = FontManager.getInstance().font
    searchText.addKeyListener(
      object : KeyAdapter() {
        private var prevWord: String? = null
        private var curPos = 0

        override fun keyReleased(e: KeyEvent) {
          try {
            searchText.font = FontManager.getInstance().font
            updateAll()

            // returnキーの時は、そこへ移動してハイライト
            if (e.keyChar == '\n') {
              val word = searchText.text
              if (word != prevWord) {
                prevWord = word
                curPos = 0
              }
              curPos = searchText(curPos + word.length, word)
              val base = baseText
              if (curPos >= 0 && base != null) {
                addHighlight(curPos, curPos + word.length, CURRENT_PAINTER)
                base.caretPosition = curPos
              }
            }
          } catch (ex: Exception) {
            errWithStackTrace(ex)
          }
        }
      }
    )
    searchCount.isOpaque = true
    searchCount.preferredSize = Dimension(75, 12)
    searchCount.horizontalAlignment = JLabel.CENTER

    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(searchText)
    add(searchCount)
  }

  fun setBaseText(textPane: JTextComponent) {
    setBaseText(textPane, null)
  }

  fun setBaseText(textPane: JTextComponent, emphasisArea: Range?) {
    val current = baseText
    if (current != null && current !== textPane) removeOwnHighlights(current)
    baseText = textPane
    this.emphasisArea = emphasisArea
  }

  fun setText(text: String?) {
    searchText.text = text
  }

  fun getText(): String = searchText.text

  private fun documentText(): String {
    val doc = baseText?.document ?: return ""
    return try {
      doc.getText(0, doc.length)
    } catch (e: BadLocationException) {
      ""
    }
  }

  private fun searchText(start: Int, word: String): Int {
    if (baseText == null) return 0
    return documentText().indexOf(word, start)
  }

  private fun addHighlight(start: Int, end: Int, painter: Highlighter.HighlightPainter) {
    try {
      baseText?.highlighter?.addHighlight(start, end, painter)
    } catch (e: BadLocationException) {
      // 範囲外は無視
    }
  }

  fun coloringSearchText(): Int {
    if (baseText == null) return 0
    val str = documentText()
    val word = searchText.text
    if (str.length > MAX_TEXT_LENGTH_FOR_HIGHLIGHTING) return -1
    if (str.isEmpty() || word.isEmpty()) return 0
    var count = 0
    var start = str.indexOf(word)
    while (start >= 0) {
      if (count < MAX_HIGHLIGHTED_MATCHES) addHighlight(start, start + word.length, MATCH_PAINTER)
      count++
      start = str.indexOf(word, start + word.length)
    }
    return count
  }

  fun coloringEmphasis() {
    val area = emphasisArea ?: return
    if (baseText == null) return
    addHighlight(area.positionStart, area.positionEnd, EMPHASIS_PAINTER)
  }

  fun coloringClear() {
    baseText?.let { removeOwnHighlights(it) }
  }

  private fun updateAll() {
    coloringClear()
    coloringEmphasis()
    updateSearchCount(coloringSearchText())
  }

  private fun updateSearchCount(count: Int) {
    val (label, color) =
      when {
        count < 0 -> "Too Long" to Color.RED
        count > 0 -> String.format("%d found", count) to Color.YELLOW
        else -> "Not found" to Color.GRAY
      }
    searchCount.background = color
    searchCount.text = label
  }

  fun textChanged() {
    updateAll()
  }
}
