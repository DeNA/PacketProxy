package packetproxy.util

import java.awt.Point
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.text.DefaultHighlighter
import javax.swing.text.JTextComponent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import packetproxy.common.Range
import packetproxy.model.Database

class SearchBoxTest {
  companion object {
    /** SearchBox は FontManager 経由で設定 DB を読むので、一時ディレクトリに DB を開いておく */
    @JvmStatic
    @BeforeAll
    fun openDatabase() {
      val dir = java.nio.file.Files.createTempDirectory("searchbox-test")
      Database.getInstance().openAt(dir.resolve("test.sqlite3").toString())
    }
  }

  private fun <T> onEdt(block: () -> T): T {
    var result: T? = null
    SwingUtilities.invokeAndWait { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  /** 画面に出さずに、スクロールできる状態にする */
  private fun inScrollPane(area: JTextArea, width: Int = 300, height: Int = 100): JScrollPane {
    val scroll = JScrollPane(area)
    scroll.setSize(width, height)
    scroll.doLayout()
    scroll.viewport.doLayout()
    return scroll
  }

  private fun highlightedStarts(text: JTextComponent): List<Int> =
    text.highlighter.highlights.map { it.startOffset }.sorted()

  @Test
  fun onlyVisibleMatchesAreHighlighted() {
    onEdt {
      val area = JTextArea("a\n".repeat(2000))
      val scroll = inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")

      assertThat(box.matchCount).isEqualTo(2000)
      assertThat(box.countLabel).isEqualTo("2000 found")
      val top = highlightedStarts(area)
      assertThat(top).isNotEmpty().hasSizeLessThan(100)
      assertThat(top.first()).isEqualTo(0)

      // 末尾までスクロールすると、そこに見えている一致に付け直す
      scroll.viewport.viewPosition = Point(0, area.height - scroll.viewport.height)
      box.refreshHighlights()
      val bottom = highlightedStarts(area)
      assertThat(bottom).isNotEmpty().hasSizeLessThan(100)
      assertThat(bottom.last()).isEqualTo(2 * 1999)
    }
  }

  @Test
  fun denseMatchesAreHighlightedUpToLimit() {
    onEdt {
      val area = JTextArea("ab".repeat(50_000))
      area.lineWrap = true
      inScrollPane(area, 2000, 2000)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")

      assertThat(box.matchCount).isEqualTo(50_000)
      assertThat(area.highlighter.highlights).hasSize(SearchBox.MAX_VISIBLE_HIGHLIGHTS)
    }
  }

  @Test
  fun adjacentMatchesShareOneHighlight() {
    onEdt {
      val area = JTextArea("xaaaay")
      inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")

      assertThat(box.matchCount).isEqualTo(4)
      assertThat(area.highlighter.highlights.map { it.startOffset to it.endOffset })
        .containsExactly(1 to 5)

      // 移動先の一致は別のハイライトにするので、そこで分かれる
      box.moveToNext()
      box.moveToNext()
      assertThat(area.highlighter.highlights.map { it.startOffset to it.endOffset })
        .containsExactlyInAnyOrder(1 to 2, 3 to 5, 2 to 3)
    }
  }

  @Test
  fun nextAndPreviousMoveThroughAllMatchesAndWrap() {
    onEdt {
      val area = JTextArea("x a b a c a")
      inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")

      box.moveToNext()
      assertThat(area.caretPosition).isEqualTo(2)
      assertThat(box.countLabel).isEqualTo("1 / 3")
      box.moveToNext()
      box.moveToNext()
      assertThat(area.caretPosition).isEqualTo(10)
      assertThat(box.countLabel).isEqualTo("3 / 3")
      box.moveToNext()
      assertThat(box.countLabel).isEqualTo("1 / 3")
      box.moveToPrevious()
      assertThat(area.caretPosition).isEqualTo(10)
      assertThat(box.countLabel).isEqualTo("3 / 3")
    }
  }

  @Test
  fun jumpToMovesToNumberedMatch() {
    onEdt {
      val area = JTextArea("x a b a c a")
      inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")

      box.jumpTo("2")
      assertThat(area.caretPosition).isEqualTo(6)
      assertThat(box.countLabel).isEqualTo("2 / 3")
      box.moveToNext()
      assertThat(box.countLabel).isEqualTo("3 / 3")
      // 範囲外は端の一致へ、数でなければ動かない
      box.jumpTo("0")
      assertThat(box.countLabel).isEqualTo("1 / 3")
      box.jumpTo("99")
      assertThat(box.countLabel).isEqualTo("3 / 3")
      box.jumpTo("x")
      assertThat(area.caretPosition).isEqualTo(10)
      assertThat(box.countLabel).isEqualTo("3 / 3")
    }
  }

  @Test
  fun refreshingKeepsHighlightsThatStayVisible() {
    onEdt {
      val area = JTextArea("a\n".repeat(2000))
      val scroll = inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")
      val before = area.highlighter.highlights.toSet()

      // 1 行だけスクロールすると、見え続けている一致のハイライトはそのまま残る
      scroll.viewport.viewPosition = Point(0, area.height / 2000)
      box.refreshHighlights()
      val after = area.highlighter.highlights.toSet()
      assertThat(after.intersect(before)).hasSize(before.size - 1)
      assertThat(after - before).hasSize(1)
    }
  }

  @Test
  fun movingStartsFromCaretWhenUserMovedIt() {
    onEdt {
      val area = JTextArea("x a b a c a")
      inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")

      box.moveToNext()
      area.caretPosition = 7
      box.moveToNext()
      assertThat(area.caretPosition).isEqualTo(10)
      area.caretPosition = 7
      box.moveToPrevious()
      assertThat(area.caretPosition).isEqualTo(6)
    }
  }

  @Test
  fun editingBaseTextSearchesAgain() {
    val area = onEdt { JTextArea("a b") }
    val box = onEdt {
      inScrollPane(area)
      SearchBox().also {
        it.setBaseText(area)
        it.setText("a")
      }
    }
    assertThat(onEdt { box.matchCount }).isEqualTo(1)
    onEdt { area.document.insertString(area.document.length, " a a", null) }
    // 探し直しは invokeLater でまとめて行われるので、EDT を一周させてから見る
    onEdt {}
    assertThat(onEdt { box.matchCount }).isEqualTo(3)
  }

  @Test
  fun switchingBaseTextRemovesHighlightsFromOld() {
    onEdt {
      val first = JTextArea("a a")
      val second = JTextArea("b")
      inScrollPane(first)
      inScrollPane(second)
      val box = SearchBox()
      box.setBaseText(first)
      box.setText("a")
      assertThat(first.highlighter.highlights).isNotEmpty()

      box.setBaseText(second)
      assertThat(first.highlighter.highlights).isEmpty()
    }
  }

  @Test
  fun indexFieldIsUsableOnlyWhenFound() {
    onEdt {
      val area = JTextArea("x a b a c a")
      inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("z")
      assertThat(box.indexFieldEnabled).isFalse()
      assertThat(box.countLabel).isEqualTo("Not found")

      box.setText("a")
      assertThat(box.indexFieldEnabled).isTrue()
      assertThat(box.countLabel).isEqualTo("3 found")
    }
  }

  @Test
  fun tooLongTextIsNotSearched() {
    onEdt {
      val area = JTextArea("a".repeat(1_000_001))
      inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area)
      box.setText("a")
      assertThat(box.matchCount).isEqualTo(0)
      assertThat(box.indexFieldEnabled).isFalse()
      assertThat(box.countLabel).isEqualTo("Too Long")
    }
  }

  @Test
  fun emphasisStaysAfterSearchingAgain() {
    onEdt {
      val area = JTextArea("x a b a c a")
      inScrollPane(area)
      val box = SearchBox()
      box.setBaseText(area, Range.of(0, 1))
      box.setText("a")
      box.setText("b")
      assertThat(area.highlighter.highlights.map { it.startOffset to it.endOffset })
        .containsExactlyInAnyOrder(0 to 1, 4 to 5)
    }
  }

  @Test
  fun replacingDocumentSearchesAgain() {
    val area = onEdt { JTextArea("a b") }
    val box = onEdt {
      inScrollPane(area)
      SearchBox().also {
        it.setBaseText(area)
        it.setText("a")
      }
    }
    onEdt {
      area.document = javax.swing.text.PlainDocument().apply { insertString(0, "a a a", null) }
    }
    onEdt {}
    assertThat(onEdt { box.matchCount }).isEqualTo(3)
    // 差し替えたあとの文書の編集でも探し直す
    onEdt { area.document.insertString(0, "a ", null) }
    onEdt {}
    assertThat(onEdt { box.matchCount }).isEqualTo(4)
  }

  @Test
  fun replacingHighlighterAddsHighlightsAgain() {
    val area = onEdt { JTextArea("x a b a") }
    onEdt {
      inScrollPane(area)
      SearchBox().also {
        it.setBaseText(area)
        it.setText("a")
      }
      area.highlighter = DefaultHighlighter()
    }
    onEdt {}
    assertThat(onEdt { area.highlighter.highlights.map { it.startOffset } })
      .containsExactlyInAnyOrder(2, 6)
  }
}
