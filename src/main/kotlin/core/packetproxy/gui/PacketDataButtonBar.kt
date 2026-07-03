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
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.LineBorder
import packetproxy.controller.ResendController
import packetproxy.controller.SinglePacketAttackController
import packetproxy.model.DiffModels
import packetproxy.model.Packet
import packetproxy.model.Packets
import packetproxy.util.CharSetUtility
import packetproxy.util.Logging.errWithStackTrace
import packetproxy.util.Logging.log

class PacketDataButtonBar(
  private val owner: JFrame,
  private val getActiveData: () -> ByteArray?,
  private val getContextPacket: () -> Packet?,
  private val getBodyData: () -> ByteArray?,
  private val getResponseData: () -> ByteArray?,
  private val markedOriginalRowHighlight: MarkedOriginalRowHighlight,
) {
  private val charSetUtility = CharSetUtility.getInstance()

  private val charSetCombo =
    JComboBox(charSetUtility.availableCharSetList.toTypedArray()).apply {
      addActionListener {
        charSetUtility.charSet = selectedItem as String
        runCatching { GUIPacket.getInstance().update() }.onFailure { errWithStackTrace(it) }
      }
      maximumSize = Dimension(150, maximumSize.height)
      addMouseListener(
        object : MouseAdapter() {
          override fun mousePressed(e: MouseEvent) {
            super.mousePressed(e)
            this@PacketDataButtonBar.updateCharSetCombo()
          }
        }
      )
      selectedItem = charSetUtility.charSetForGUIComponent
    }

  private val copyUrlBodyButton =
    JButton("copy Method+URL+Body").apply {
      addActionListener {
        runCatching {
            val data = getActiveData() ?: return@addActionListener
            if (data.isEmpty()) {
              return@addActionListener
            }
            val packet = getContextPacket() ?: return@addActionListener
            copyMethodUrlBody(data, packet)
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val copyBodyButton =
    JButton("copy Body").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            val data = resolveDataForCopyBody() ?: return@addActionListener
            if (data.isEmpty()) {
              return@addActionListener
            }
            copyBody(data)
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val copyUrlButton =
    JButton("copy URL").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            val data = getActiveData() ?: return@addActionListener
            if (data.isEmpty()) {
              return@addActionListener
            }
            val packet = getContextPacket() ?: return@addActionListener
            copyUrl(data, packet)
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val resendButton =
    JButton("send").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            withActivePacket { data, packet, packetId ->
              ResendController.getInstance().resend(packet.getOneShotPacket(data))
              markResent(packet, packetId)
            }
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val resendMultipleButton =
    JButton("send x 20").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            withActivePacket { data, packet, packetId ->
              ResendController.getInstance().resend(packet.getOneShotPacket(data), 20)
              markResent(packet, packetId)
            }
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val attackButton =
    JButton("send x 20 (single-packet attack)").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            withActivePacket { data, packet, packetId ->
              SinglePacketAttackController(packet.getOneShotPacket(data)).attack(20)
              markResent(packet, packetId)
            }
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val sendToResenderButton =
    JButton("send to Resender").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            withActivePacket { data, packet, packetId ->
              packet.setResend()
              Packets.getInstance().update(packet)
              GUIResender.getInstance().addResends(packet.getOneShotPacket(data))
              GUIHistory.getInstance().updateRequestOne(packetId)
            }
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val stopDiffButton =
    JButton("stop diff").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            if (!markedOriginalRowHighlight.hasMarkedOriginal) {
              return@addActionListener
            }
            DiffModels.clearOriginal()
            markedOriginalRowHighlight.restoreMarkedRowAndClear()
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val diffButton =
    JButton("diff!!").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            val data = resolveDataForDiff() ?: return@addActionListener
            DiffModels.markAsTarget(data)
            GUIDiffDialogParent(owner).showDialog()
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  private val diffOrigButton =
    JButton("mark as orig").apply {
      alignmentX = 0.5f
      addActionListener {
        runCatching {
            val data = resolveDataForDiff() ?: return@addActionListener
            if (markedOriginalRowHighlight.hasMarkedOriginal) {
              DiffModels.clearOriginal()
              markedOriginalRowHighlight.restoreMarkedRowAndClear()
            }
            DiffModels.markAsOriginal(data)
            markedOriginalRowHighlight.markCurrentRowAsOriginal()
            log("Diff: original text was saved!")
          }
          .onFailure { errWithStackTrace(it) }
      }
    }

  fun createPanel(): JComponent {
    val diffPanel =
      JPanel().apply {
        add(diffOrigButton)
        add(diffButton)
        add(stopDiffButton)
        border = LineBorder(Color.BLACK, 1, true)
        layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      }

    val buttonPanel =
      JPanel().apply {
        add(charSetCombo)
        add(copyUrlBodyButton)
        add(copyBodyButton)
        add(copyUrlButton)
        add(resendButton)
        add(resendMultipleButton)
        add(attackButton)
        add(sendToResenderButton)
        add(JLabel("  diff: "))
        add(diffPanel)
        layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      }

    val centeredPanel = ScrollableCenteredPanel()
    centeredPanel.add(buttonPanel)
    return ScrollableButtonPanel.createScrollPane(centeredPanel)
  }

  private fun updateCharSetCombo() {
    charSetCombo.removeAllItems()
    for (charSetName in charSetUtility.availableCharSetList) {
      charSetCombo.addItem(charSetName)
    }
    val charSetName = CharSetUtility.getInstance().charSetForGUIComponent
    if (charSetUtility.availableCharSetList.contains(charSetName)) {
      charSetCombo.selectedItem = charSetName
    } else {
      charSetCombo.selectedIndex = 0
    }
  }

  private inline fun withActivePacket(block: (ByteArray, Packet, Int) -> Unit) {
    val data = getActiveData() ?: return
    if (data.isEmpty()) {
      return
    }
    val packetId = GUIHistory.getInstance().selectedPacketId
    val packet = getContextPacket() ?: return
    block(data, packet, packetId)
  }

  private fun markResent(packet: Packet, packetId: Int) {
    packet.setResend()
    Packets.getInstance().update(packet)
    GUIHistory.getInstance().updateRequestOne(packetId)
  }

  private fun resolveDataForCopyBody(): ByteArray? =
    MergedRowDataResolver.resolve(
      owner = owner,
      message = "Which body do you want to copy?",
      title = "Select Copy Target",
      isMergedRow = MergedRowDataResolver.isSelectedRowMerged(),
      requestData = { getBodyData() },
      responseData = { getResponseData() },
    )

  private fun resolveDataForDiff(): ByteArray? =
    MergedRowDataResolver.resolve(
      owner = owner,
      message = "Which data do you want to use for Diff?",
      title = "Select Diff Target",
      isMergedRow = MergedRowDataResolver.isSelectedRowMerged(),
      requestData = { getActiveData() },
      responseData = { getResponseData() },
    )
}
