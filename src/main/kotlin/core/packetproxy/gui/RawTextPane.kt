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
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Base64
import java.util.EventListener
import java.util.regex.Pattern
import javax.swing.AbstractAction
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.EventListenerList
import javax.swing.text.DefaultEditorKit
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit
import org.fife.ui.rsyntaxtextarea.Style
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rtextarea.RTextArea
import org.fife.ui.rtextarea.RTextAreaEditorKit
import packetproxy.VulCheckerManager
import packetproxy.common.BinaryBuffer
import packetproxy.common.FontManager
import packetproxy.common.I18nString
import packetproxy.common.Range
import packetproxy.common.Utils
import packetproxy.util.CharSetUtility
import packetproxy.util.Logging.errWithStackTrace
import packetproxy.util.PacketProxyUtility

/**
 * パケットの生データを表示・編集するテキスト領域。
 *
 * 色付けは RSyntaxTextArea と PacketTokenMaker に任せ、描画する行だけを解析するので巨大なパケットでも固まらない。編集内容は raw_data
 * (BinaryBuffer) に 同期し、getData() で取り出す。
 */
class RawTextPane @Throws(Exception::class) constructor() : RSyntaxTextArea() {
  companion object {
    private const val TEXT_TRIMMING_SIZE = 300000
    // バイナリデータの表示はテキストデータの表示に比べ大幅に時間がかかることを考慮して設定
    private const val BINARY_TRIMMING_SIZE = 10000
    private const val DEFAULT_SHOW_SIZE = 2000

    /** 旧実装の String.getBytes() / new String(byte[]) と同じく、プラットフォームの既定の文字コードで変換する */
    private val PLATFORM_CHARSET: Charset = Charset.defaultCharset()

    /** String.split("\\.") と同じく、末尾の空要素を捨てて分割する（alg: none の JWT は末尾が "." になる） */
    private val DOT = Pattern.compile("\\.")

    private val WRAP_PAIRS =
      listOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')

    private const val INSERT_TAB_ACTION = "packetproxy.insertTab"

    private fun isStandardEditingAction(action: String): Boolean =
      action.startsWith("caret-") ||
        action.startsWith("selection-") ||
        action.startsWith("select-") ||
        action.startsWith("RTA.Selection") ||
        action in
          setOf(
            DefaultEditorKit.copyAction,
            DefaultEditorKit.cutAction,
            DefaultEditorKit.pasteAction,
            DefaultEditorKit.insertBreakAction,
            DefaultEditorKit.deletePrevCharAction,
            DefaultEditorKit.deleteNextCharAction,
            DefaultEditorKit.deletePrevWordAction,
            DefaultEditorKit.deleteNextWordAction,
            DefaultEditorKit.pageUpAction,
            DefaultEditorKit.pageDownAction,
            RTextAreaEditorKit.rtaDeletePrevWordAction,
            RTextAreaEditorKit.rtaUndoAction,
            RTextAreaEditorKit.rtaRedoAction,
          )

    private fun joinWithDots(parts: List<ByteArray>): ByteArray =
      parts.reduce { a, b ->
        ArrayUtils.addAll(ArrayUtils.addAll(a, *".".toByteArray(PLATFORM_CHARSET)), *b)
      }
  }

  fun interface DataChangedListener : EventListener {
    fun dataChanged(data: ByteArray)
  }

  private val charSetUtility: CharSetUtility = CharSetUtility.getInstance()
  private var prevTextPanel = ""
  private val rawData = BinaryBuffer()
  private var initCount = 0
  private var shownData: ByteArray? = null
  private var showAll = false
  private var initFlg = false
  private var finFlg = false
  private var schemeFont: Font? = null
  private var styleUpdateScheduled = false
  private var settingText = false
  private val dataChangedListeners = EventListenerList()

  init {
    configureEditor()
    installRawDataSync()
    popupMenu = buildPopupMenu()
  }

  private fun configureEditor() {
    PacketTokenMaker.register()
    lineWrap = true
    wrapStyleWord = false
    highlightCurrentLine = false
    isCodeFoldingEnabled = false
    isBracketMatchingEnabled = false
    isAutoIndentEnabled = false
    closeCurlyBraces = false
    closeMarkupTags = false
    isClearWhitespaceLinesEnabled = false
    markOccurrences = false
    // 以下は RSyntaxTextArea 独自の挙動で、パケットの編集を意図せず変えたり外部リソースを読んだりしうるので止める
    insertPairedCharacters = false // JSON 中で " を打つと "" と自動で閉じる
    dragEnabled = false // 選択範囲のドラッグで文字列が移動する
    hyperlinksEnabled = false
    useFocusableTips = false // ツールチップが HTML として描画される
    restrictKeyBindings()
    installWrapSelectionKeys()
    try {
      font = FontManager.getInstance().font
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
    applySyntaxScheme()
    syntaxEditingStyle = PacketTokenMaker.STYLE_HTTP
  }

  /** フォントに合わせて、JSON / HTTP パラメータの配色を作り直す */
  private fun applySyntaxScheme() {
    val font = font
    if (font == null || font == schemeFont) return
    schemeFont = font
    val bold = font.deriveFont(Font.BOLD)
    val scheme = SyntaxScheme(true)
    for (i in 0 until scheme.styleCount) scheme.setStyle(i, Style(Color.BLACK, null, null))
    scheme.setStyle(Token.VARIABLE, Style(Color(0x92, 0x00, 0x6B), null, bold))
    scheme.setStyle(Token.LITERAL_STRING_DOUBLE_QUOTE, Style(Color(0x00, 0x64, 0x00), null, null))
    scheme.setStyle(Token.LITERAL_NUMBER_DECIMAL_INT, Style(Color(0x00, 0x00, 0xFF), null, null))
    scheme.setStyle(Token.LITERAL_BOOLEAN, Style(Color(0x00, 0x00, 0xFF), null, null))
    scheme.setStyle(Token.RESERVED_WORD_2, Style(Color(0x80, 0x80, 0x80), null, bold))
    scheme.setStyle(Token.SEPARATOR, Style(Color.BLACK, null, bold))
    scheme.setStyle(Token.DATA_TYPE, Style(Color.BLUE, null, null))
    scheme.setStyle(Token.ANNOTATION, Style(Color.RED, null, null))
    syntaxScheme = scheme
  }

  /**
   * キー操作の割り当てを標準的な編集操作だけにする。Cmd+D の行削除がアプリの Drop を奪う、TAB で選択行の先頭にタブが入る、INSERT で上書きモードになる、
   * といったパケットを意図せず書き換える操作を防ぐ。文字入力 (KEY_TYPED) の割り当ては、外すと文字が入力できなくなるので残す
   */
  private fun restrictKeyBindings() {
    val inputMap = getInputMap(JComponent.WHEN_FOCUSED)
    val keys = HashSet<KeyStroke>()
    var m: InputMap? = inputMap
    while (m != null) {
      m.keys()?.let { keys.addAll(it) }
      m = m.parent
    }
    for (ks in keys) {
      if (ks.keyEventType == KeyEvent.KEY_TYPED) continue
      val action = inputMap.get(ks) as? String ?: continue
      when {
        // 書式付きコピーは copy() を通らず、末尾改行の除去も効かないので通常のコピーにする
        action == RSyntaxTextAreaEditorKit.rstaCopyAsStyledTextAction ->
          inputMap.put(ks, DefaultEditorKit.copyAction)
        action == RSyntaxTextAreaEditorKit.rstaCutAsStyledTextAction ->
          inputMap.put(ks, DefaultEditorKit.cutAction)
        // 旧実装 (JTextPane) と同じく、選択範囲をタブ 1 文字で置き換える
        action == DefaultEditorKit.insertTabAction -> inputMap.put(ks, INSERT_TAB_ACTION)
        !isStandardEditingAction(action) -> inputMap.put(ks, "none")
      }
    }
    actionMap.put(
      INSERT_TAB_ACTION,
      object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          if (this@RawTextPane.isEditable && this@RawTextPane.isEnabled) replaceSelection("\t")
        }
      },
    )
    // 旧実装と同じく、どの OS でも Cmd/Ctrl+Shift+Z を redo にする
    // 画面のない環境 (CI のテスト) でも例外を出さない、RSyntaxTextArea 自身と同じ修飾キー
    val menuMask = RTextArea.getDefaultModifier()
    inputMap.put(
      KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask or InputEvent.SHIFT_DOWN_MASK),
      RTextAreaEditorKit.rtaRedoAction,
    )
  }

  /** 選択中に括弧や引用符を打つと選択範囲を囲む。選択がなければ普通に1文字入力する（自動で閉じることはしない） */
  private fun installWrapSelectionKeys() {
    val inputMap = getInputMap(JComponent.WHEN_FOCUSED)
    for ((open, close) in WRAP_PAIRS) {
      val name = "packetproxy.wrapSelection.$open"
      inputMap.put(KeyStroke.getKeyStroke(open), name)
      actionMap.put(
        name,
        object : AbstractAction() {
          override fun actionPerformed(e: ActionEvent) {
            // AbstractAction 自身にも isEnabled があるので、テキスト領域のものを明示する
            if (!this@RawTextPane.isEditable || !this@RawTextPane.isEnabled) return
            val start = selectionStart
            if (start == selectionEnd) {
              replaceSelection(open.toString())
              return
            }
            val selected = selectedText
            replaceSelection("$open$selected$close")
            select(start + 1, start + 1 + selected.length)
          }
        },
      )
    }
  }

  /** 編集のたびに呼ばれるので、EDT の次の周回で1回だけ字句解析の方式を決め直す */
  private fun scheduleSyntaxStyleUpdate() {
    // setText は方式を自分で決めるので、その中で起きる挿入・削除では予約しない
    if (settingText || styleUpdateScheduled) return
    styleUpdateScheduled = true
    SwingUtilities.invokeLater {
      styleUpdateScheduled = false
      updateSyntaxStyle()
    }
  }

  private fun updateSyntaxStyle() {
    val style = PacketTokenMaker.detectStyle(super.getText())
    if (style != syntaxEditingStyle) syntaxEditingStyle = style
  }

  private fun installRawDataSync() {
    document.addDocumentListener(
      object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) {
          scheduleSyntaxStyleUpdate()
          try {
            if (initFlg) {
              initCount += e.length
              if (initCount == rawData.lengthInUTF8) {
                initFlg = false
                finFlg = false
                initCount = 0
                prevTextPanel = e.document.getText(0, e.document.length)
              }
              return
            }
            val str = e.document.getText(e.offset, e.length)
            prevTextPanel = e.document.getText(0, e.document.length)
            val before = prevTextPanel.substring(0, e.offset)
            rawData.insert(
              before.toByteArray(PLATFORM_CHARSET).size,
              str.toByteArray(PLATFORM_CHARSET),
            )
          } catch (ex: Exception) {
            errWithStackTrace(ex)
          }
        }

        override fun removeUpdate(e: DocumentEvent) {
          scheduleSyntaxStyleUpdate()
          try {
            if (finFlg) {
              finFlg = false
              return
            }
            val before = prevTextPanel.substring(0, e.offset)
            val removed = prevTextPanel.substring(e.offset, e.offset + e.length)
            prevTextPanel = e.document.getText(0, e.document.length)
            rawData.remove(
              before.toByteArray(PLATFORM_CHARSET).size,
              removed.toByteArray(PLATFORM_CHARSET).size,
            )
          } catch (ex: Exception) {
            errWithStackTrace(ex)
          }
        }

        override fun changedUpdate(e: DocumentEvent) {}
      }
    )
    addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          try {
            if (showAll) return
            shownData?.let { setData(it, false) }
          } catch (ex: Exception) {
            errWithStackTrace(ex)
          }
        }

        override fun mouseReleased(e: MouseEvent) {
          showDecodedTooltipOnSelectedText()
        }
      }
    )
    addKeyListener(
      object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent) {
          val current = getData()
          if (current.contentEquals(shownData)) return // 文字列の中身が変化してない場合は戻る
          shownData = current
          callDataChanged(current)
        }

        override fun keyTyped(e: KeyEvent) {
          callDataChanged(getData())
        }
      }
    )
  }

  private fun buildPopupMenu(): JPopupMenu {
    val menu = JPopupMenu()

    addMenuTitle(menu, "VulCheck Helpers")
    for (vulCheckerName in VulCheckerManager.getInstance().allVulCheckers.keys) {
      val vulChecker = VulCheckerManager.getInstance().createInstance(vulCheckerName)
      addMenuItem(menu, vulChecker.name) {
        val range = Range.of(selectionStart, selectionEnd)
        val packet = GUIPacket.getInstance().packet
        GUIVulCheckHelper.getInstance()
          .addVulCheck(vulChecker, packet.getOneShotPacket(getData()), range)
      }
    }

    menu.addSeparator()
    addMenuTitle(menu, "Decoders")
    addMenuItem(menu, "URL Decoder") {
      val charSetName = guessCharSetName()
      val data = selectedString(charSetName).toByteArray(PLATFORM_CHARSET)
      showDialog(
        URLDecoder.decode(String(data, PLATFORM_CHARSET), charSetName).toByteArray(PLATFORM_CHARSET)
      )
    }
    addMenuItem(menu, "Base64 / Base64url Decoder") {
      val data = selectedString(guessCharSetName()).toByteArray(PLATFORM_CHARSET)
      val isBase64Url = data.contains('_'.code.toByte()) || data.contains('-'.code.toByte())
      showDialog(
        if (isBase64Url) Base64.getUrlDecoder().decode(data) else Base64.getDecoder().decode(data)
      )
    }
    addMenuItem(menu, "JWT Decoder") {
      val charSetName = guessCharSetName()
      val data = selectedString(charSetName).toByteArray(PLATFORM_CHARSET)
      val parts =
        DOT.split(String(data, Charset.forName(charSetName))).map {
          Base64.getUrlDecoder().decode(it)
        }
      showDialog(joinWithDots(parts))
    }
    addMenuItem(menu, "Unicode Unescaper") {
      val charSetName = guessCharSetName()
      val data = selectedString(charSetName).toByteArray(PLATFORM_CHARSET)
      val selection = String(data, Charset.forName(charSetName))
      showDialog(StringEscapeUtils.unescapeJava(selection).toByteArray(PLATFORM_CHARSET))
    }

    menu.addSeparator()
    addMenuTitle(menu, "Encoders")
    addMenuItem(menu, "URL Encoder") {
      val charSetName = guessCharSetName()
      val data = selectedString(charSetName).toByteArray(PLATFORM_CHARSET)
      showDialog(
        URLEncoder.encode(String(data, PLATFORM_CHARSET), charSetName).toByteArray(PLATFORM_CHARSET)
      )
    }
    addMenuItem(menu, "Base64 Encoder") {
      showDialog(
        Base64.getEncoder().encode(selectedString(guessCharSetName()).toByteArray(PLATFORM_CHARSET))
      )
    }
    addMenuItem(menu, "Base64url Encoder") {
      showDialog(
        Base64.getUrlEncoder()
          .encode(selectedString(guessCharSetName()).toByteArray(PLATFORM_CHARSET))
      )
    }
    addMenuItem(menu, "JWT Encoder") {
      val charSetName = guessCharSetName()
      val data = selectedString(charSetName).toByteArray(PLATFORM_CHARSET)
      val parts =
        DOT.split(String(data, Charset.forName(charSetName))).map { part ->
          val encoded =
            if (part[0] == '{') {
              Base64.getUrlEncoder().encode(part.toByteArray(PLATFORM_CHARSET))
            } else {
              // signature: 32 バイトのダミーを返す
              Base64.getUrlEncoder()
                .encode("12345678901234567890123456789012".toByteArray(PLATFORM_CHARSET))
            }
          StringUtils.strip(String(encoded, PLATFORM_CHARSET), "=").toByteArray(PLATFORM_CHARSET)
        }
      showDialog(joinWithDots(parts))
    }
    addMenuItem(menu, "Unicode Escaper") {
      val selection = selectedString(guessCharSetName())
      val unicode = selection.map { String.format("\\u%04X", it.code) }.joinToString("")
      showDialog(unicode.toByteArray(PLATFORM_CHARSET))
    }
    return menu
  }

  private fun addMenuTitle(menu: JPopupMenu, label: String) {
    val title = JMenuItem(I18nString.get(label))
    title.font = FontManager.getInstance().uiCaptionFont
    title.isEnabled = false
    menu.add(title)
  }

  private fun addMenuItem(menu: JPopupMenu, label: String, action: () -> Unit) {
    val item = JMenuItem(label)
    item.addActionListener {
      try {
        action()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(item)
  }

  private fun guessCharSetName(): String {
    if (charSetUtility.isAuto) charSetUtility.setGuessedCharSet(getData())
    return charSetUtility.charSet
  }

  /** 選択範囲の文字列。表示中の文字列ではなく、生データを指定の文字コードで読み直して切り出す */
  private fun selectedString(charSetName: String): String =
    String(getData(), Charset.forName(charSetName)).substring(selectionStart, selectionEnd)

  private fun showDialog(data: ByteArray) {
    val dlg = GUIDecoderDialog()
    dlg.setData(data)
    dlg.showDialog()
  }

  @Throws(Exception::class)
  fun setData(data: ByteArray, trimming: Boolean) {
    font = FontManager.getInstance().font
    applySyntaxScheme()
    shownData = data
    // データが多いと遅いので長いデータをトリミングする
    val tooLong =
      data.size > TEXT_TRIMMING_SIZE ||
        (PacketProxyUtility.getInstance().isBinaryData(data, BINARY_TRIMMING_SIZE) &&
          data.size > BINARY_TRIMMING_SIZE)
    if (trimming && tooLong) {
      showAll = false
      val head = data.copyOf(minOf(data.size, DEFAULT_SHOW_SIZE))
      val charSetName = guessCharSetName()
      text =
        "********************\n  This data is too long.\n  If you want to show all message, please click this panel\n********************\n\n\n\n\n\n" +
          String(head, Charset.forName(charSetName))
    } else {
      showAll = true
      setData(data)
      callDataChanged(data)
    }
    caretPosition = 0
  }

  private fun showDecodedTooltipOnSelectedText() {
    val area = Range.of(selectionStart, selectionEnd)
    if (area.length == 0) return
    val request = Utils.getSelectedCharacters(shownData, area.positionStart, area.positionEnd)
    toolTipText = GUITooltipDecodeMessage(request).decodeMessage()
  }

  fun addDataChangedListener(listener: DataChangedListener) {
    dataChangedListeners.add(DataChangedListener::class.java, listener)
  }

  private fun callDataChanged(data: ByteArray) {
    for (listener in dataChangedListeners.getListeners(DataChangedListener::class.java)) {
      listener.dataChanged(data)
    }
  }

  override fun copy() {
    val selected = selectedText
    if (selected.isNullOrEmpty()) {
      super.copy()
      return
    }
    val selection = StringSelection(selected.trimEnd('\n', '\r'))
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
  }

  override fun setEditable(b: Boolean) {
    super.setEditable(b)
    if (!b) background = Color.WHITE
  }

  @Throws(Exception::class)
  fun setData(data: ByteArray) {
    initFlg = true
    finFlg = true
    initCount = 0
    prevTextPanel = ""
    rawData.reset(data)
    val charSetName = guessCharSetName()
    text = String(data, Charset.forName(charSetName))
  }

  fun getData(): ByteArray = rawData.toByteArray()

  /* バイナリデータだとデータが壊れるので要注意 */
  override fun getText(): String = String(rawData.toByteArray(), PLATFORM_CHARSET)

  /* バイナリデータだとデータが壊れるので要注意 */
  override fun setText(text: String?) {
    try {
      val t = text ?: ""
      finFlg = true
      initFlg = true
      initCount = 0
      prevTextPanel = ""
      rawData.reset(t.toByteArray(PLATFORM_CHARSET))
      // 字句解析の方式を先に決めておくと、テキスト挿入後の再解析が1回で済む
      val style = PacketTokenMaker.detectStyle(t)
      if (style != syntaxEditingStyle) syntaxEditingStyle = style
      settingText = true
      try {
        super.setText(t)
      } finally {
        settingText = false
      }
      discardAllEdits()
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }
}
