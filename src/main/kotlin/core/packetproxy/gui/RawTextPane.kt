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
import java.awt.Toolkit
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Arrays
import java.util.Base64
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import packetproxy.VulCheckerManager
import packetproxy.common.FontManager
import packetproxy.common.I18nString
import packetproxy.common.Range
import packetproxy.common.Utils
import packetproxy.util.CharSetUtility
import packetproxy.util.Logging.errWithStackTrace

internal open class RawTextPane @Throws(Exception::class) constructor() : ExtendedTextPane() {

  private val charSetUtility: CharSetUtility = CharSetUtility.getInstance()

  init {
    val menu = JPopupMenu()

    addKeyListener(
      object : KeyAdapter() {
        @Suppress("DEPRECATION")
        override fun keyPressed(e: KeyEvent) {
          when (e.keyCode) {
            KeyEvent.VK_Z -> {
              if ((Toolkit.getDefaultToolkit().menuShortcutKeyMask and e.modifiers) > 0) {
                /* Command key */

                if (e.isShiftDown) {
                  /* Ctrl-Shift-Z */

                  if (undo_manager.canRedo()) undo_manager.redo()
                } else {
                  /* Ctrl-Z */

                  if (undo_manager.canUndo()) undo_manager.undo()
                }
                e.consume()
              }
            }
            KeyEvent.VK_Y -> {
              if ((Toolkit.getDefaultToolkit().menuShortcutKeyMask and e.modifiers) > 0) {
                /* Command key */

                if (undo_manager.canRedo()) /* Ctrl-Y */ undo_manager.redo()
                e.consume()
              }
            }
          }
        }
      }
    )

    val vulCheckers = JMenuItem(I18nString.get("VulCheck Helpers"))
    vulCheckers.font = FontManager.getInstance().uiCaptionFont
    vulCheckers.isEnabled = false
    menu.add(vulCheckers)

    for (vulCheckerName in VulCheckerManager.getInstance().allVulCheckers.keys) {
      val vulChecker = VulCheckerManager.getInstance().createInstance(vulCheckerName)
      val vulCheckerItem = JMenuItem(vulChecker.name)
      vulCheckerItem.addActionListener {
        try {
          val range = Range.of(selectionStart, selectionEnd)
          val packet = GUIPacket.getInstance().packet
          GUIVulCheckHelper.getInstance()
            .addVulCheck(vulChecker, packet.getOneShotPacket(getData()), range)
        } catch (e: Exception) {
          errWithStackTrace(e)
        }
      }
      menu.add(vulCheckerItem)
    }

    menu.addSeparator()
    val title_decoders = JMenuItem(I18nString.get("Decoders"))
    title_decoders.font = FontManager.getInstance().uiCaptionFont
    title_decoders.isEnabled = false
    menu.add(title_decoders)

    val url_decoder = JMenuItem("URL Decoder")
    url_decoder.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val chasetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val data =
          String(getData(), charset(chasetName))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val dlg = GUIDecoderDialog()
        dlg.setData(
          URLDecoder.decode(String(data, Charset.defaultCharset()), chasetName)
            .toByteArray(Charset.defaultCharset())
        )
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(url_decoder)

    val base64_decoder = JMenuItem("Base64 / Base64url Decoder")
    base64_decoder.addActionListener {
      try {
        val position_start = selectionStart
        val position_end = selectionEnd

        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val data =
          String(getData(), charset(charSetUtility.charSet))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val dlg = GUIDecoderDialog()
        if (
          Utils.indexOf(data, 0, data.size, "_".toByteArray(Charset.defaultCharset())) >= 0 ||
            Utils.indexOf(data, 0, data.size, "-".toByteArray(Charset.defaultCharset())) >= 0
        ) { // base64url
          dlg.setData(Base64.getUrlDecoder().decode(data))
        } else { // base64
          dlg.setData(Base64.getDecoder().decode(data))
        }
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(base64_decoder)

    val jwt_decoder = JMenuItem("JWT Decoder")
    jwt_decoder.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val charSetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val data =
          String(getData(), charset(charSetName))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val dlg = GUIDecoderDialog()
        dlg.setData(
          Arrays.stream("\\.".toPattern().split(String(data, charset(charSetName))))
            .map(Base64.getUrlDecoder()::decode)
            .reduce { a, b ->
              ArrayUtils.addAll(
                ArrayUtils.addAll(a, *".".toByteArray(Charset.defaultCharset())),
                *b,
              )
            }
            .get()
        )
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(jwt_decoder)

    val unicode_unescaper = JMenuItem("Unicode Unescaper")
    unicode_unescaper.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val charSetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val data =
          String(getData(), charset(charSetName))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val selection = String(data, charset(charSetName))
        val unescaped = StringEscapeUtils.unescapeJava(selection)
        val dlg = GUIDecoderDialog()
        dlg.setData(unescaped.toByteArray(Charset.defaultCharset()))
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(unicode_unescaper)

    menu.addSeparator()
    val title_encoders = JMenuItem(I18nString.get("Encoders"))
    title_encoders.font = FontManager.getInstance().uiCaptionFont
    title_encoders.isEnabled = false
    menu.add(title_encoders)

    val url_encoder = JMenuItem("URL Encoder")
    url_encoder.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val charSetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val data =
          String(getData(), charset(charSetName))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val dlg = GUIDecoderDialog()
        dlg.setData(
          URLEncoder.encode(String(data, Charset.defaultCharset()), charSetName)
            .toByteArray(Charset.defaultCharset())
        )
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(url_encoder)

    val base64_encoder = JMenuItem("Base64 Encoder")
    base64_encoder.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val charSetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val data =
          String(getData(), charset(charSetName))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val dlg = GUIDecoderDialog()
        dlg.setData(Base64.getEncoder().encode(data))
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(base64_encoder)

    val base64url_encoder = JMenuItem("Base64url Encoder")
    base64url_encoder.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val charSetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val data =
          String(getData(), charset(charSetName))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val dlg = GUIDecoderDialog()
        dlg.setData(Base64.getUrlEncoder().encode(data))
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(base64url_encoder)

    val jwt_encoder = JMenuItem("JWT Encoder")
    jwt_encoder.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val charSetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val data =
          String(getData(), charset(charSetName))
            .substring(position_start, position_end)
            .toByteArray(Charset.defaultCharset())
        val dlg = GUIDecoderDialog()
        dlg.setData(
          Arrays.stream("\\.".toPattern().split(String(data, charset(charSetName))))
            .map { a ->
              val b =
                if (a[0] == '{') {
                  String(
                    Base64.getUrlEncoder().encode(a.toByteArray(Charset.defaultCharset())),
                    Charset.defaultCharset(),
                  )
                } else {
                  /* signature */
                  String(
                    Base64.getUrlEncoder()
                      .encode(
                        "12345678901234567890123456789012".toByteArray(Charset.defaultCharset())
                      ),
                    Charset.defaultCharset(),
                  ) /* return 32 bytes data */
                }
              StringUtils.strip(b, "=").toByteArray(Charset.defaultCharset())
            }
            .reduce { a, b ->
              ArrayUtils.addAll(
                ArrayUtils.addAll(a, *".".toByteArray(Charset.defaultCharset())),
                *b,
              )
            }
            .get()
        )
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(jwt_encoder)

    val unicode_escaper = JMenuItem("Unicode Escaper")
    unicode_escaper.addActionListener {
      try {
        if (charSetUtility.isAuto) {
          charSetUtility.setGuessedCharSet(getData())
        }
        val charSetName = charSetUtility.charSet
        val position_start = selectionStart
        val position_end = selectionEnd
        val selection =
          String(getData(), charset(charSetName)).substring(position_start, position_end)
        val sb = StringBuilder()
        for (c in selection.toCharArray()) {
          sb.append(String.format("\\u%04X", c.code))
        }
        val unicode = sb.toString()
        val dlg = GUIDecoderDialog()
        dlg.setData(unicode.toByteArray(Charset.defaultCharset()))
        dlg.showDialog()
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
    menu.add(unicode_escaper)

    addMouseListener(
      object : MouseAdapter() {
        override fun mouseReleased(event: MouseEvent) {
          if (Utils.isWindows() && event.isPopupTrigger) {
            menu.show(event.component, event.x, event.y)
          }
        }

        override fun mousePressed(event: MouseEvent) {
          if (event.isPopupTrigger) {
            menu.show(event.component, event.x, event.y)
          }
        }
      }
    )
  }

  override fun prepareTextForCopy(selected: String): String {
    return stripTrailingNewlines(selected)
  }

  override fun setEditable(b: Boolean) {
    super.setEditable(b)
    if (!b) {
      background = Color.WHITE
    }
  }

  @Throws(Exception::class)
  override fun setData(data: ByteArray?) {
    init_flg = true
    fin_flg = true
    init_count = 0
    prev_text_panel = ""
    raw_data.reset(data)
    if (charSetUtility.isAuto) {
      charSetUtility.setGuessedCharSet(getData())
    }
    val charSetName = charSetUtility.charSet
    text = String(data!!, charset(charSetName))
    undo_manager.discardAllEdits()
  }

  override fun getData(): ByteArray {
    return raw_data.toByteArray()
  }

  /* バイナリデータだとデータが壊れるので要注意 */
  override fun getText(): String {
    return String(raw_data.toByteArray(), Charset.defaultCharset())
  }

  /* バイナリデータだとデータが壊れるので要注意 */
  override fun setText(text: String?) {
    try {
      fin_flg = true
      init_flg = true
      init_count = 0
      prev_text_panel = ""
      raw_data.reset(text!!.toByteArray(Charset.defaultCharset()))
      super.setText(text)
      undo_manager.discardAllEdits()
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }
}

private fun stripTrailingNewlines(s: String): String {
  var end = s.length
  while (end > 0) {
    val c = s[end - 1]
    if (c == '\n' || c == '\r') {
      end--
      continue
    }
    break
  }
  return if (end == s.length) s else s.substring(0, end)
}
