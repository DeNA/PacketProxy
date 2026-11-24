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
import java.awt.Graphics
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Document
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent
import javax.swing.text.View
import packetproxy.common.FontManager
import packetproxy.common.Range
import packetproxy.util.Logging.errWithStackTrace

/**
 * テキストの検索と強調表示。
 *
 * 一致はすべて位置だけを覚えておき、色は画面に見えている範囲の一致にだけ付ける。長い 1 行に多くのハイライトが乗ると描画が極端に遅くなるためで、スクロールするたびに付け直す。
 *
 * 前後の一致へは、ボタンか Enter / Shift+Enter で移動する。番号を入れて、その一致へ移ることもできる。
 */
class SearchBox @Throws(Exception::class) constructor() : JPanel() {
  companion object {
    private const val MAX_TEXT_LENGTH_FOR_HIGHLIGHTING = 1_000_000

    /** 見えている範囲で色を付ける件数の上限。1 文字の検索などで一致が密集したときに、描画が遅くならないようにする */
    internal const val MAX_VISIBLE_HIGHLIGHTS = 500

    /** 覚えておく位置の数の上限 */
    private const val MAX_CACHED_SHAPES = 10_000

    /** 見えている範囲の文字位置 [from, to)。画面に出ていなければ null */
    private fun visibleOffsets(text: JTextComponent): Pair<Int, Int>? {
      val r = text.visibleRect
      if (r.isEmpty) return null
      val from = text.viewToModel2D(Point(r.x, r.y))
      val to = text.viewToModel2D(Point(r.x + r.width, r.y + r.height))
      if (from < 0 || to < 0) return null
      return from to to + 1
    }
  }

  private var baseText: JTextComponent? = null
  private var baseDocument: Document? = null
  private var emphasisArea: Range? = null
  private val searchText = JTextField()
  private val indexField = JTextField(5)
  private val searchCount = JLabel("Not found")
  private val nextButton = JButton("▼")
  private val previousButton = JButton("▲")

  private var matches = SearchMatches.EMPTY
  private var tooLong = false

  /** 移動先として選んでいる一致の番号。選んでいなければ -1 */
  private var current = -1

  /** 付けているハイライト。(開始, 終了) をまとめた値から引く */
  private val shown = HashMap<Long, Shown>()

  private class Shown(val tag: Any, val painter: Highlighter.HighlightPainter)

  /**
   * 塗る場所の計算結果。
   *
   * 折り返した長い 1 行では、位置の計算に 1 回で数ミリ秒かかる。描くたびに計算し直すと、スクロールのたびに数十件ぶん待たされるので覚えておく。
   *
   * 同じ文字範囲を同じ領域 (bounds) に描くなら、塗る場所も同じになる、という前提で覚えている。RSyntaxTextArea の折り返し表示は、行を描くときに自分自身を view
   * として渡すので、この前提が成り立つ。
   *
   * 大きさが変わると bounds も変わるので、キーが一致しなくなる。文書、フォント、折り返しの設定など、bounds に表れない変化では捨てる。
   */
  private val shapeCache = HashMap<ShapeKey, Rectangle>()

  private data class ShapeKey(
    val p0: Int,
    val p1: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
  )

  /** 計算した場所を [shapeCache] に覚えておく塗り方 */
  private inner class CachingPainter(color: Color) :
    DefaultHighlighter.DefaultHighlightPainter(color) {
    override fun paintLayer(
      g: Graphics,
      offs0: Int,
      offs1: Int,
      bounds: Shape,
      c: JTextComponent,
      view: View,
    ): Shape? {
      val b = bounds.bounds
      val key = ShapeKey(offs0, offs1, b.x, b.y, b.width, b.height)
      val cached = shapeCache[key]
      if (cached != null) {
        g.color = color ?: c.selectionColor
        g.fillRect(cached.x, cached.y, cached.width, cached.height)
        return cached
      }
      val shape = super.paintLayer(g, offs0, offs1, bounds, c, view) ?: return null
      if (shapeCache.size >= MAX_CACHED_SHAPES) shapeCache.clear()
      shapeCache[key] = Rectangle(shape.bounds)
      return shape
    }
  }

  private val matchPainter = CachingPainter(Color.YELLOW)
  private val currentPainter = CachingPainter(Color.MAGENTA)
  private val emphasisPainter = CachingPainter(Color(0xB0, 0xF0, 0xB0))
  private var refreshScheduled = false
  private var researchScheduled = false

  private val viewListener =
    object : ComponentAdapter() {
      override fun componentMoved(e: ComponentEvent) = scheduleRefresh()

      override fun componentResized(e: ComponentEvent) {
        shapeCache.clear()
        scheduleRefresh()
      }
    }
  private val ancestorListener =
    object : HierarchyBoundsAdapter() {
      override fun ancestorResized(e: HierarchyEvent) = scheduleRefresh()
    }
  private val baseDocumentListener =
    object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent) {
        shapeCache.clear()
        scheduleResearch()
      }

      override fun removeUpdate(e: DocumentEvent) {
        shapeCache.clear()
        scheduleResearch()
      }

      override fun changedUpdate(e: DocumentEvent) {}
    }

  /** フォントや折り返しなど、表示に関わる設定が変わったら位置を計算し直す */
  private val basePropertyListener = PropertyChangeListener { e ->
    shapeCache.clear()
    // 見た目の切り替えなどで Highlighter ごと入れ替わったら、付けていたハイライトは消えている
    if (e.propertyName == "highlighter") {
      shown.clear()
      scheduleResearch()
    }
  }
  private val documentSwapListener = PropertyChangeListener {
    val base = baseText ?: return@PropertyChangeListener
    listenDocument(base.document)
    scheduleResearch()
  }

  init {
    searchText.font = FontManager.getInstance().font
    searchText.document.addDocumentListener(
      object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = research()

        override fun removeUpdate(e: DocumentEvent) = research()

        override fun changedUpdate(e: DocumentEvent) {}
      }
    )
    searchText.addActionListener { moveToNext() }
    val previousKey = "packetproxy.searchPrevious"
    searchText
      .getInputMap(JComponent.WHEN_FOCUSED)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), previousKey)
    searchText.actionMap.put(
      previousKey,
      object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) = moveToPrevious()
      },
    )

    previousButton.toolTipText = "Previous (Shift+Enter)"
    nextButton.toolTipText = "Next (Enter)"
    for (button in listOf(previousButton, nextButton)) {
      button.isFocusable = false
      button.margin = Insets(0, 4, 0, 4)
    }
    previousButton.addActionListener { moveToPrevious() }
    nextButton.addActionListener { moveToNext() }

    indexField.toolTipText = "Go to match (Enter)"
    indexField.horizontalAlignment = JTextField.RIGHT
    indexField.maximumSize = indexField.preferredSize
    indexField.addActionListener { jumpTo(indexField.text) }

    searchCount.isOpaque = true
    searchCount.preferredSize = Dimension(80, 12)
    searchCount.horizontalAlignment = JLabel.CENTER

    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(searchText)
    // 検索欄のすぐ隣に、Enter と同じ「次へ」を置く
    add(nextButton)
    add(previousButton)
    add(indexField)
    add(searchCount)
    updateSearchCount()
  }

  fun setBaseText(textPane: JTextComponent) {
    setBaseText(textPane, null)
  }

  fun setBaseText(textPane: JTextComponent, emphasisArea: Range?) {
    val old = baseText
    if (old !== textPane) {
      if (old != null) {
        removeOwnHighlights(old)
        old.removeComponentListener(viewListener)
        old.removeHierarchyBoundsListener(ancestorListener)
        old.removePropertyChangeListener(basePropertyListener)
        old.removePropertyChangeListener("document", documentSwapListener)
      }
      textPane.addComponentListener(viewListener)
      textPane.addHierarchyBoundsListener(ancestorListener)
      textPane.addPropertyChangeListener(basePropertyListener)
      textPane.addPropertyChangeListener("document", documentSwapListener)
      listenDocument(textPane.document)
      baseText = textPane
      matches = SearchMatches.EMPTY
      current = -1
      shown.clear()
      shapeCache.clear()
    }
    this.emphasisArea = emphasisArea
  }

  private fun listenDocument(document: Document?) {
    baseDocument?.removeDocumentListener(baseDocumentListener)
    document?.addDocumentListener(baseDocumentListener)
    baseDocument = document
  }

  fun setText(text: String?) {
    searchText.text = text
  }

  fun getText(): String = searchText.text

  /** 検索対象のテキストが変わったときに呼ぶ */
  fun textChanged() {
    research()
  }

  internal val matchCount: Int
    get() = matches.size

  internal val countLabel: String
    get() = "${indexField.text} ${searchCount.text}".trim()

  internal val indexFieldEnabled: Boolean
    get() = indexField.isEnabled

  private fun documentText(doc: Document): String {
    return try {
      doc.getText(0, doc.length)
    } catch (e: BadLocationException) {
      ""
    }
  }

  private fun isOwnPainter(painter: Highlighter.HighlightPainter?): Boolean =
    painter === matchPainter || painter === currentPainter || painter === emphasisPainter

  /** 選択範囲などのハイライトは残し、このクラスが付けたものだけを消す */
  private fun removeOwnHighlights(text: JTextComponent) {
    val highlighter = text.highlighter
    for (h in highlighter.highlights) {
      if (isOwnPainter(h.painter)) highlighter.removeHighlight(h)
    }
    shown.clear()
  }

  private fun addHighlight(start: Int, end: Int, painter: Highlighter.HighlightPainter): Any? =
    try {
      baseText?.highlighter?.addHighlight(start, end, painter)
    } catch (e: BadLocationException) {
      null
    }

  private fun scheduleResearch() {
    if (researchScheduled) return
    researchScheduled = true
    SwingUtilities.invokeLater { if (researchScheduled) research() }
  }

  private fun scheduleRefresh() {
    if (refreshScheduled) return
    refreshScheduled = true
    SwingUtilities.invokeLater { if (refreshScheduled) refreshHighlights() }
  }

  /** 一致を探し直し、色を付け直す。移動先の選択は解除する */
  private fun research() {
    researchScheduled = false
    try {
      searchText.font = FontManager.getInstance().font
      val base = baseText
      if (base != null) removeOwnHighlights(base)
      current = -1
      val doc = base?.document
      tooLong = doc != null && doc.length > MAX_TEXT_LENGTH_FOR_HIGHLIGHTING
      // 本文を編集するたびに呼ばれるので、探さないときは全文をコピーしない
      val word = searchText.text
      matches =
        if (doc == null || tooLong || word.isEmpty()) SearchMatches.EMPTY
        else SearchMatches.find(documentText(doc), word)
      val area = emphasisArea
      if (base != null && area != null) {
        addHighlight(area.positionStart, area.positionEnd, emphasisPainter)
      }
      refreshHighlights()
      updateSearchCount()
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }

  /**
   * 見えている範囲の一致と、移動先の一致に色を付け直す。
   *
   * ハイライトを付けると、その場所を描き直すために位置を計算する。スクロールで少し動いただけのときに全部を付け直さないよう、変わったものだけを付け外しする。
   */
  internal fun refreshHighlights() {
    refreshScheduled = false
    val base = baseText ?: return
    val wanted = LinkedHashMap<Long, Highlighter.HighlightPainter>()
    val visible = if (matches.size == 0) null else visibleOffsets(base)
    if (visible != null) {
      // 隣り合う一致 (aaaa を a で検索したときなど) は 1 つのハイライトにまとめる
      var runStart = -1
      var runEnd = -1
      fun flush() {
        if (runStart < 0) return
        wanted[key(runStart, runEnd)] = matchPainter
        runStart = -1
      }
      for (i in matches.indicesOverlapping(visible.first, visible.second)) {
        if (wanted.size >= MAX_VISIBLE_HIGHLIGHTS) break
        // 移動先の一致は、あとで別の色で塗る
        if (i == current) {
          flush()
          continue
        }
        if (runStart >= 0 && matches.start(i) == runEnd) {
          runEnd = matches.end(i)
        } else {
          flush()
          runStart = matches.start(i)
          runEnd = matches.end(i)
        }
      }
      if (wanted.size < MAX_VISIBLE_HIGHLIGHTS) flush()
    }
    if (current >= 0) wanted[key(matches.start(current), matches.end(current))] = currentPainter

    val highlighter = base.highlighter
    val iter = shown.entries.iterator()
    while (iter.hasNext()) {
      val (k, s) = iter.next()
      if (wanted[k] !== s.painter) {
        highlighter.removeHighlight(s.tag)
        iter.remove()
      }
    }
    for ((k, painter) in wanted) {
      if (k in shown) continue
      addHighlight((k ushr 32).toInt(), k.toInt(), painter)?.let { shown[k] = Shown(it, painter) }
    }
  }

  private fun key(start: Int, end: Int): Long = (start.toLong() shl 32) or end.toLong()

  /** 移動先の一致の番号。カーソルを動かされていたら、選択は外れたものとして -1 を返す */
  private fun currentAtCaret(base: JTextComponent): Int =
    if (current >= 0 && base.caretPosition == matches.start(current)) current else -1

  internal fun moveToNext() {
    val base = baseText ?: return
    if (matches.size == 0) return
    val c = currentAtCaret(base)
    moveTo(if (c >= 0) (c + 1) % matches.size else matches.nextFrom(base.caretPosition))
  }

  internal fun moveToPrevious() {
    val base = baseText ?: return
    if (matches.size == 0) return
    val c = currentAtCaret(base)
    moveTo(
      if (c >= 0) (c - 1 + matches.size) % matches.size
      else matches.previousBefore(base.caretPosition)
    )
  }

  /** 入力された番号 (1 から) の一致へ移る。範囲外なら端の一致へ */
  internal fun jumpTo(input: String) {
    val n = input.trim().toIntOrNull()
    if (n == null || matches.size == 0) {
      updateSearchCount()
      return
    }
    moveTo(n.coerceIn(1, matches.size) - 1)
  }

  private fun moveTo(index: Int) {
    val base = baseText ?: return
    if (index < 0) return
    current = index
    try {
      base.caretPosition = matches.start(index)
      base.modelToView2D(matches.start(index))?.bounds?.let { base.scrollRectToVisible(it) }
    } catch (e: BadLocationException) {
      // 範囲外は無視
    } catch (e: IllegalArgumentException) {
      // 文書が短くなり、探し直す前の古い位置を指しているとき
    }
    refreshHighlights()
    updateSearchCount()
  }

  private fun updateSearchCount() {
    val found = !tooLong && matches.size > 0
    // 出し入れすると検索欄の幅が変わるので、欄は常に出しておき、使えるかどうかだけを切り替える
    indexField.isEnabled = found
    indexField.text = if (current >= 0) "${current + 1}" else ""
    val (label, color) =
      when {
        tooLong -> "Too Long" to Color.RED
        !found -> "Not found" to Color.GRAY
        current < 0 -> "${matches.size} found" to Color.YELLOW
        else -> "/ ${matches.size}" to Color.YELLOW
      }
    searchCount.background = color
    searchCount.text = label
    revalidate()
  }
}
