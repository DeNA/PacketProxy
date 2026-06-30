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
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.KeyStroke
import org.apache.commons.io.FileUtils
import packetproxy.controller.ResendController
import packetproxy.http.Http
import packetproxy.http.SessionProfileAuthorizationExtractor
import packetproxy.model.Packets
import packetproxy.util.Logging.errWithStackTrace

/**
 * Extracted popup menu builder and action wiring for GUIHistory. Reduces the size and
 * responsibility in GUIHistory.java.
 */
object GUIHistoryContextMenuFactory {

  class Handles(
    val menu: JPopupMenu,
    val send: JMenuItem,
    val sendToResender: JMenuItem,
    val copy: JMenuItem,
    val copyAll: JMenuItem,
  )

  @JvmStatic
  fun build(
    context: GUIHistory,
    owner: JFrame,
    table: JTable,
    guiPacket: GUIPacket,
    packets: Packets,
    colorManager: TableCustomColorManager,
    packetColorGreen: Color,
    packetColorBrown: Color,
    packetColorYellow: Color,
  ): Handles {
    val maskKey = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    val menu = JPopupMenu()

    val send =
      createMenuItem("send", KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_S, maskKey)) {
        try {
          val id = context.selectedPacketId
          val packet = packets.query(id)
          val data = guiPacket.data
          if (packet == null) {
            return@createMenuItem
          }
          ResendController.getInstance().resend(packet.getOneShotPacket(data))
          packet.setResend()
          packets.update(packet)
          context.updateRequestOne(context.selectedPacketId)
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val sendToResender =
      createMenuItem(
        "send to Resender",
        KeyEvent.VK_R,
        KeyStroke.getKeyStroke(KeyEvent.VK_R, maskKey),
      ) {
        try {
          val packet = guiPacket.packet
          packet.setResend()
          packets.update(packet)
          if (packet.modifiedData.isEmpty()) {
            GUIResender.getInstance().addResends(packet.getOneShotFromDecodedData())
          } else {
            GUIResender.getInstance().addResends(packet.getOneShotFromModifiedData())
          }
          context.updateRequestOne(context.selectedPacketId)
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val createSessionProfile =
      createMenuItem("create session profile", -1, null) {
        try {
          val data = guiPacket.data
          val authorization = SessionProfileAuthorizationExtractor.extract(data)
          if (authorization == null || authorization.isEmpty()) {
            JOptionPane.showMessageDialog(
              owner,
              "No Authorization header found in the current request.",
              "Message",
              JOptionPane.INFORMATION_MESSAGE,
            )
            return@createMenuItem
          }
          val dlg = GUIOptionSessionProfileDialog(owner, null)
          dlg.showDialog(authorization)
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val copyAll =
      createMenuItem(
        "copy Method + URL + Body",
        KeyEvent.VK_M,
        KeyStroke.getKeyStroke(KeyEvent.VK_M, maskKey),
      ) {
        try {
          val packet = guiPacket.packet
          copyMethodUrlBody(packet.decodedData, packet)
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val copy =
      createMenuItem("copy URL", KeyEvent.VK_Y, KeyStroke.getKeyStroke(KeyEvent.VK_Y, maskKey)) {
        try {
          val id = context.selectedPacketId
          val packet = packets.query(id)
          copyUrl(packet.decodedData, packet)
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val bulkSender =
      createMenuItem("send to Bulk Sender", -1, null) {
        try {
          val packet = guiPacket.packet
          if (packet.modifiedData.isEmpty()) {
            GUIBulkSender.getInstance().add(packet.getOneShotFromDecodedData(), packet.id)
          } else {
            GUIBulkSender.getInstance().add(packet.getOneShotFromModifiedData(), packet.id)
          }
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val saveAll =
      createSaveMenuItem(owner, "save all data to file", "packet.dat") {
        guiPacket.packet.receivedData
      }

    val saveHttpBody =
      createSaveMenuItem(owner, "save HTTP body to file", "body.dat") {
        Http.create(guiPacket.packet.decodedData).body
      }

    val addColorG =
      createColorMenuItem(
        table,
        packets,
        colorManager,
        packetColorGreen,
        "green",
        "add color (green)",
      )
    val addColorB =
      createColorMenuItem(
        table,
        packets,
        colorManager,
        packetColorBrown,
        "brown",
        "add color (brown)",
      )
    val addColorY =
      createColorMenuItem(
        table,
        packets,
        colorManager,
        packetColorYellow,
        "yellow",
        "add color (yellow)",
      )
    val clearColor = createClearColorMenuItem(table, colorManager)

    val deleteSelectedItems =
      createMenuItem("delete selected items", -1, null) {
        try {
          val selectedRows = table.selectedRows
          for (i in selectedRows.indices) {
            val requestPacketId = table.getValueAt(selectedRows[i], 0) as Int
            colorManager.clear(requestPacketId)

            // マージされた行の場合、レスポンスパケットも一緒に削除する（DB残留を防ぐ）
            val responsePacketId = context.getResponsePacketIdForRequest(requestPacketId)
            if (responsePacketId != -1) {
              colorManager.clear(responsePacketId)
              packets.delete(packets.query(responsePacketId))
            }

            packets.delete(packets.query(requestPacketId))
          }
          context.updateAll()
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val deleteAll =
      createMenuItem("delete all items", -1, null) {
        try {
          for (i in 0 until table.rowCount) {
            val id = table.getValueAt(i, 0) as Int
            colorManager.clear(id)
          }
          packets.deleteAll()
          context.updateAll()
        } catch (ex: Exception) {
          errWithStackTrace(ex)
        }
      }

    val copyAsCurl =
      createMenuItem(
        "copy as curl",
        -1,
        null,
        ActionListener {
          try {
            val http = Http.create(guiPacket.packet.decodedData)
            val headerFields = http.header.fields
            val commandList = ArrayList<String>()
            commandList.add("curl")
            val url = http.getURL(guiPacket.packet.serverPort, guiPacket.packet.useSSL)
            commandList.add(String.format("'%s'", url))
            commandList.add("-X")
            commandList.add(http.method)
            for (hf in headerFields) {
              commandList.add("-H")
              commandList.add(String.format("'%s: %s'", hf.name, hf.value))
            }
            val body = String(http.body)
            if (body.trim().isNotEmpty()) {
              commandList.add("--data")
              commandList.add(String.format("'%s'", body))
            }
            commandList.add("--compressed")
            val command = StringSelection(commandList.joinToString(" "))
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(command, command)
          } catch (ex: Exception) {
            errWithStackTrace(ex)
          }
        },
      )

    menu.add(send)
    menu.add(sendToResender)
    menu.add(createSessionProfile)
    menu.add(copyAll)
    menu.add(copy)
    menu.add(bulkSender)
    menu.add(saveAll)
    menu.add(saveHttpBody)
    menu.add(addColorG)
    menu.add(addColorB)
    menu.add(addColorY)
    menu.add(clearColor)
    menu.add(copyAsCurl)
    menu.add(deleteSelectedItems)
    menu.add(deleteAll)

    return Handles(menu, send, sendToResender, copy, copyAll)
  }

  private fun createMenuItem(
    name: String,
    key: Int,
    hotkey: KeyStroke?,
    listener: ActionListener,
  ): JMenuItem {
    val out = JMenuItem(name)
    if (key >= 0) {
      out.mnemonic = key
    }
    if (hotkey != null) {
      out.accelerator = hotkey
    }
    out.addActionListener(listener)
    return out
  }

  private fun createMenuItem(
    name: String,
    key: Int,
    hotkey: KeyStroke?,
    listener: (ActionEvent) -> Unit,
  ): JMenuItem = createMenuItem(name, key, hotkey, ActionListener(listener))

  private fun createSaveMenuItem(
    owner: JFrame,
    label: String,
    defaultFileName: String,
    dataSupplier: () -> ByteArray,
  ): JMenuItem {
    return createMenuItem(label, -1, null) {
      val filechooser = WriteFileChooserWrapper(owner, "dat", defaultFileName)
      filechooser.addFileChooserListener(
        object : WriteFileChooserWrapper.FileChooserListener {
          override fun onApproved(file: File, extension: String) {
            try {
              val data = dataSupplier()
              FileUtils.writeByteArrayToFile(file, data)
              JOptionPane.showMessageDialog(owner, String.format("%sに保存しました！", file.path))
            } catch (ex: Exception) {
              errWithStackTrace(ex)
              JOptionPane.showMessageDialog(null, "データの保存に失敗しました。")
            }
          }

          override fun onCanceled() {}

          override fun onError() {
            JOptionPane.showMessageDialog(null, "データの保存に失敗しました。")
          }
        }
      )
      filechooser.showSaveDialog()
    }
  }

  private fun createColorMenuItem(
    table: JTable,
    packets: Packets,
    colorManager: TableCustomColorManager,
    awtColor: Color,
    dbColorName: String,
    label: String,
  ): JMenuItem {
    return createMenuItem(label, -1, null) {
      try {
        val selectedRows = table.selectedRows
        for (i in selectedRows.indices) {
          val id = table.getValueAt(selectedRows[i], 0) as Int
          colorManager.add(id, awtColor)
          val packet = packets.query(id)
          packet.color = dbColorName
          packets.update(packet)
        }
      } catch (ex: Exception) {
        errWithStackTrace(ex)
      }
    }
  }

  private fun createClearColorMenuItem(
    table: JTable,
    colorManager: TableCustomColorManager,
  ): JMenuItem {
    return createMenuItem("clear color", -1, null) {
      try {
        val selectedRows = table.selectedRows
        for (i in selectedRows.indices) {
          val id = table.getValueAt(selectedRows[i], 0) as Int
          colorManager.clear(id)
        }
      } catch (ex: Exception) {
        errWithStackTrace(ex)
      }
    }
  }
}
