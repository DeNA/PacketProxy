package packetproxy.gui

import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import packetproxy.model.Database
import packetproxy.util.CharSetUtility

class RawTextPaneTest {
  companion object {
    /** RawTextPane は FontManager 経由で設定 DB を読むので、一時ディレクトリに DB を開いておく */
    @JvmStatic
    @BeforeAll
    fun openDatabase() {
      val dir = java.nio.file.Files.createTempDirectory("rawtextpane-test")
      Database.getInstance().openAt(dir.resolve("test.sqlite3").toString())
    }
  }

  private val charSetField =
    CharSetUtility::class.java.getDeclaredField("charSet").apply { isAccessible = true }
  private val isAutoField =
    CharSetUtility::class.java.getDeclaredField("isAuto").apply { isAccessible = true }
  private var savedCharSet: Any? = null
  private var savedIsAuto = false

  /** 文字コードの推定に左右されないよう UTF-8 に固定する。シングルトンなので、他のテストに影響しないよう後で戻す */
  @BeforeEach
  fun setUp() {
    val utility = CharSetUtility.getInstance()
    savedCharSet = charSetField.get(utility)
    savedIsAuto = isAutoField.getBoolean(utility)
    charSetField.set(utility, "UTF-8")
    isAutoField.setBoolean(utility, false)
  }

  @AfterEach
  fun tearDown() {
    val utility = CharSetUtility.getInstance()
    charSetField.set(utility, savedCharSet)
    isAutoField.setBoolean(utility, savedIsAuto)
  }

  private fun <T> onEdt(block: () -> T): T {
    var result: T? = null
    SwingUtilities.invokeAndWait { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  @Test
  fun setData_roundTripsAndEditsUpdateRawData() {
    onEdt {
      val pane = RawTextPane()
      val data = "GET / HTTP/1.1\r\nHost: あ\r\n\r\n{\"a\":1}".toByteArray()
      pane.setData(data, true)
      assertThat(pane.getData()).isEqualTo(data)
      assertThat(pane.syntaxEditingStyle).isEqualTo(PacketTokenMaker.STYLE_HTTP_JSON)

      pane.document.insertString(0, "X", null)
      assertThat(String(pane.getData())).startsWith("XGET")
      pane.document.remove(0, 1)
      assertThat(pane.getData()).isEqualTo(data)
    }
  }

  @Test
  fun largeJson_setDataAndPaintFinishQuickly() {
    // 描画にはウィンドウが要るので、画面のない環境 (CI) では手元での確認に任せる
    assumeFalse(GraphicsEnvironment.isHeadless())
    val body =
      (0 until 6000).joinToString(",", "[", "]") {
        """{"id":$it,"name":"user$it","ok":true,"v":null}"""
      }
    val data = ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + body).toByteArray()
    assertThat(data.size).isGreaterThan(250_000)

    val elapsedMs = onEdt {
      val pane = RawTextPane()
      // 描画には displayable なコンポーネントが要るので、表示はしないフレームに載せる
      val frame = javax.swing.JFrame()
      frame.contentPane.add(pane)
      frame.setSize(800, 600)
      frame.addNotify()
      pane.setSize(800, 600)
      val start = System.nanoTime()
      pane.setData(data, false)
      val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
      val g = image.createGraphics()
      pane.paint(g)
      g.dispose()
      val ms = (System.nanoTime() - start) / 1_000_000
      frame.dispose()
      ms
    }
    println("setData+paint for ${data.size} bytes (1 line JSON): $elapsedMs ms")
    assertThat(elapsedMs).isLessThan(3000)
  }

  /** フォーカスのない状態ではキーイベントが届かないので、文字入力に割り当てられたアクションを直接呼ぶ */
  private fun typeChar(pane: RawTextPane, c: Char) {
    val name = pane.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(c))
    val action = if (name != null) pane.actionMap.get(name) else pane.keymap.defaultAction
    action.actionPerformed(ActionEvent(pane, ActionEvent.ACTION_PERFORMED, c.toString()))
  }

  @Test
  fun typingQuoteWithoutSelectionDoesNotAutoClose() {
    onEdt {
      val pane = RawTextPane()
      pane.setData("{}".toByteArray(), false)
      pane.caretPosition = 1
      typeChar(pane, '"')
      typeChar(pane, '(')
      assertThat(pane.text).isEqualTo("{\"(}")
    }
  }

  @Test
  fun typingBracketWithSelectionWrapsIt() {
    onEdt {
      val pane = RawTextPane()
      pane.setData("a=b&c=d".toByteArray(), false)
      pane.select(2, 3)
      typeChar(pane, '(')
      assertThat(pane.text).isEqualTo("a=(b)&c=d")
      assertThat(pane.selectedText).isEqualTo("b")
      typeChar(pane, '"')
      assertThat(pane.text).isEqualTo("a=(\"b\")&c=d")
    }
  }

  @Test
  fun onlyStandardEditingShortcutsAreBound() {
    onEdt {
      val pane = RawTextPane()
      val inputMap = pane.getInputMap(JComponent.WHEN_FOCUSED)
      val menu = org.fife.ui.rtextarea.RTextArea.getDefaultModifier()
      fun action(code: Int, mods: Int) = inputMap.get(KeyStroke.getKeyStroke(code, mods))

      // アプリ側のショートカット (Drop / Forward など) を奪わない。
      // RSyntaxTextArea は OS によって割り当てないキーもある (Linux の Ctrl+/ など) ので、未割り当ても許す
      for (code in listOf(KeyEvent.VK_D, KeyEvent.VK_J, KeyEvent.VK_SLASH)) {
        assertThat(action(code, menu)).describedAs(KeyEvent.getKeyText(code)).isIn(null, "none")
      }
      // 書式付きコピーではなく通常のコピー
      assertThat(action(KeyEvent.VK_C, menu))
        .isEqualTo(javax.swing.text.DefaultEditorKit.copyAction)
      assertThat(action(KeyEvent.VK_Z, menu or InputEvent.SHIFT_DOWN_MASK))
        .isEqualTo(org.fife.ui.rtextarea.RTextAreaEditorKit.rtaRedoAction)
      assertThat(action(KeyEvent.VK_Z, menu))
        .isEqualTo(org.fife.ui.rtextarea.RTextAreaEditorKit.rtaUndoAction)
      assertThat(action(KeyEvent.VK_A, menu))
        .isEqualTo(javax.swing.text.DefaultEditorKit.selectAllAction)
    }
  }

  private fun runBoundAction(pane: RawTextPane, ks: KeyStroke) {
    val name = pane.getInputMap(JComponent.WHEN_FOCUSED).get(ks)
    pane.actionMap.get(name).actionPerformed(ActionEvent(pane, ActionEvent.ACTION_PERFORMED, ""))
  }

  @Test
  fun keysWithoutModifiersDoNotRewriteBeyondTyping() {
    onEdt {
      val pane = RawTextPane()
      val inputMap = pane.getInputMap(JComponent.WHEN_FOCUSED)
      fun ks(code: Int, mods: Int = 0) = KeyStroke.getKeyStroke(code, mods)

      // TAB は選択範囲をタブ 1 文字で置き換える（各行の先頭にタブを足さない）
      pane.setData("a\n\tb\nc".toByteArray(), false)
      pane.selectAll()
      runBoundAction(pane, ks(KeyEvent.VK_TAB))
      assertThat(String(pane.getData())).isEqualTo("\t")

      // 行頭の空白を削る Shift+TAB、上書きモードに切り替える INSERT は無効
      assertThat(inputMap.get(ks(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))).isEqualTo("none")
      assertThat(inputMap.get(ks(KeyEvent.VK_INSERT))).isEqualTo("none")

      // 書式付きではない通常のコピー・カット
      val copy = javax.swing.text.DefaultEditorKit.copyAction
      val cut = javax.swing.text.DefaultEditorKit.cutAction
      assertThat(inputMap.get(ks(KeyEvent.VK_COPY))).isEqualTo(copy)
      assertThat(inputMap.get(ks(KeyEvent.VK_CUT))).isEqualTo(cut)
      assertThat(inputMap.get(ks(KeyEvent.VK_DELETE, InputEvent.SHIFT_DOWN_MASK))).isEqualTo(cut)

      // Enter は改行だけを入れる（インデントを足さない）
      pane.setData("  ab".toByteArray(), false)
      pane.caretPosition = 3
      runBoundAction(pane, ks(KeyEvent.VK_ENTER))
      assertThat(String(pane.getData())).isEqualTo("  a\nb")
    }
  }

  @Test
  fun typingClosingCharactersInsertsOnlyThatCharacter() {
    onEdt {
      val pane = RawTextPane()
      pane.setData("{\n  ab".toByteArray(), false)
      pane.caretPosition = pane.document.length - 1
      typeChar(pane, '}')
      typeChar(pane, '/')
      assertThat(String(pane.getData())).isEqualTo("{\n  a}/b")
    }
  }

  @Test
  fun setDataNotifiesDataChangedListeners() {
    onEdt {
      val pane = RawTextPane()
      val received = mutableListOf<ByteArray>()
      pane.addDataChangedListener { received.add(it) }
      val data = "a=b".toByteArray()
      pane.setData(data, true)
      assertThat(received).containsExactly(data)
    }
  }

  @Test
  fun copyStripsTrailingNewlines() {
    // システムのクリップボードを使うので、画面のない環境 (CI) では動かない
    assumeFalse(GraphicsEnvironment.isHeadless())
    onEdt {
      val pane = RawTextPane()
      pane.setData("abc\r\n\r\n".toByteArray(), false)
      pane.selectAll()
      pane.copy()
      val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
      assertThat(clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor)).isEqualTo("abc")
    }
  }

  @Test
  fun syntaxStyleFollowsDocumentEdits() {
    val pane = onEdt { RawTextPane() }
    onEdt {
      pane.setData("HTTP/1.1 200 OK\r\n\r\nhello".toByteArray(), false)
      assertThat(pane.syntaxEditingStyle).isEqualTo(PacketTokenMaker.STYLE_HTTP)
      val doc = pane.document
      doc.remove(doc.length - 5, 5)
      doc.insertString(doc.length, "{\"a\":1}", null)
    }
    // 方式の再判定は invokeLater で1回にまとめて行われるので、EDT を一周させてから見る
    onEdt {}
    assertThat(onEdt { pane.syntaxEditingStyle }).isEqualTo(PacketTokenMaker.STYLE_HTTP_JSON)
  }
}
