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

import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.nio.charset.Charset
import java.util.Arrays
import java.util.EventListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.UndoableEditEvent
import javax.swing.event.UndoableEditListener
import javax.swing.undo.UndoManager
import org.apache.commons.lang3.ArrayUtils
import packetproxy.common.BinaryBuffer
import packetproxy.common.FontManager
import packetproxy.common.Utils
import packetproxy.util.CharSetUtility
import packetproxy.util.Logging.errWithStackTrace
import packetproxy.util.PacketProxyUtility

internal abstract class ExtendedTextPane @Throws(Exception::class) constructor() :
  PlainTextCopyTextPane() {

  companion object {
    private const val serialVersionUID: Long = 3879881178060039018L
    private const val TEXT_TRIMMING_SIZE = 300000
    private const val BINARY_TRIMMING_SIZE = 10000 // バイナリデータの表示はテキストデータの表示に比べ大幅に時間がかかることを考慮して設定
    private const val DEFAULT_SHOW_SIZE = 2000
  }

  private val editor = WrapEditorKit(ByteArray(0))

  @JvmField var prev_text_panel: String = ""

  @JvmField var undo_manager: UndoManager = UndoManager()

  @JvmField var raw_data: BinaryBuffer = BinaryBuffer()

  @JvmField var init_count: Int = 0

  private var data: ByteArray? = null
  private var show_all: Boolean = false

  @JvmField var init_flg: Boolean = false

  @JvmField var fin_flg: Boolean = false

  init {
    setEditorKit(editor)
    setFont(FontManager.getInstance().font)
    document.addDocumentListener(
      object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) {
          try {
            if (init_flg) {
              init_count += e.length
              if (init_count == raw_data.lengthInUTF8) {
                init_flg = false
                fin_flg = false
                init_count = 0
                prev_text_panel = e.document.getText(0, e.document.length)
              }
              return
            }
            val str = e.document.getText(e.offset, e.length)
            prev_text_panel = e.document.getText(0, e.document.length)
            val before_string = prev_text_panel.substring(0, e.offset)
            raw_data.insert(
              before_string.toByteArray(Charset.defaultCharset()).size,
              str.toByteArray(Charset.defaultCharset()),
            )
          } catch (e1: Exception) {
            errWithStackTrace(e1)
          }
        }

        override fun removeUpdate(e: DocumentEvent) {
          try {
            if (fin_flg) {
              fin_flg = false
              return
            }
            val before_removed_string = prev_text_panel.substring(0, e.offset)
            val removed_string = prev_text_panel.substring(e.offset, e.offset + e.length)
            prev_text_panel = e.document.getText(0, e.document.length)
            raw_data.remove(
              before_removed_string.toByteArray(Charset.defaultCharset()).size,
              removed_string.toByteArray(Charset.defaultCharset()).size,
            )
            // Logging.log(String.format("remove: <%s> %d %d", removed_string,
            // removed_string.getBytes().length, e.getLength()));
          } catch (e1: Exception) {
            errWithStackTrace(e1)
          }
        }

        override fun changedUpdate(e: DocumentEvent) {}
      }
    )
    document.addUndoableEditListener(
      object : UndoableEditListener {
        override fun undoableEditHappened(e: UndoableEditEvent) {
          if (e.edit is DocumentEvent) {
            if ((e.edit as DocumentEvent).type == DocumentEvent.EventType.CHANGE) {
              /* スタイルの編集の場合は無視 */
              return
            }
          }
          undo_manager.addEdit(e.edit)
        }
      }
    )
    addMouseListener(
      object : MouseListener {
        override fun mouseClicked(e: MouseEvent) {
          try {
            if (show_all) {
              return
            }
            setData(data, false)
          } catch (e1: Exception) {
            errWithStackTrace(e1)
          }
        }

        override fun mouseEntered(e: MouseEvent) {}

        override fun mouseExited(e: MouseEvent) {}

        override fun mousePressed(e: MouseEvent) {}

        override fun mouseReleased(e: MouseEvent) {
          showDecodedTooltipOnSelectedText()
        }
      }
    )
    addKeyListener(
      object : KeyListener {
        override fun keyReleased(arg0: KeyEvent) {
          // テキストの色変更
          if (Arrays.equals(data, getData())) {
            return
          }
          data = getData() // 文字列の中身が変化してない場合は戻る
          callDataChanged(data)
        }

        override fun keyTyped(arg0: KeyEvent) {
          callDataChanged(getData())
        }

        override fun keyPressed(e: KeyEvent) {}
      }
    )
  }

  @Throws(Exception::class)
  open fun setData(data: ByteArray?, trimming: Boolean) {
    setFont(FontManager.getInstance().font)
    this.data = data
    // データが多いと遅いので長いデータをトリミングする
    if (
      trimming &&
        (data!!.size > TEXT_TRIMMING_SIZE ||
          (PacketProxyUtility.getInstance().isBinaryData(data, BINARY_TRIMMING_SIZE) &&
            data.size > BINARY_TRIMMING_SIZE))
    ) {
      show_all = false
      val head = ArrayUtils.subarray(data, 0, DEFAULT_SHOW_SIZE)
      val charSetUtility = CharSetUtility.getInstance()
      if (charSetUtility.isAuto) {
        charSetUtility.setGuessedCharSet(getData())
      }
      val charSetName = charSetUtility.charSet
      text =
        "********************\n  This data is too long.\n  If you want to show all message, please click this panel\n********************\n\n\n\n\n\n" +
          String(head, charset(charSetName))
    } else {
      show_all = true
      setData(data)
      callDataChanged(data)
    }
    caretPosition = 0
  }

  private fun showDecodedTooltipOnSelectedText() {
    val position_start = selectionStart
    val position_end = selectionEnd
    if (position_end - position_start == 0) {
      return
    }

    val request = Utils.getSelectedCharacters(data, position_start, position_end)
    toolTipText = GUITooltipDecodeMessage(request).decodeMessage()
  }

  interface DataChangedListener : EventListener {
    fun dataChanged(data: ByteArray?)
  }

  open fun addDataChangedListener(listener: DataChangedListener) {
    listenerList.add(DataChangedListener::class.java, listener)
  }

  protected open fun callDataChanged(data: ByteArray?) {
    for (listener in listenerList.getListeners(DataChangedListener::class.java)) {
      listener.dataChanged(data)
    }
  }

  @Throws(Exception::class) abstract fun setData(data: ByteArray?)

  abstract fun getData(): ByteArray?
}
