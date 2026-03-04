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

import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.border.TitledBorder
import javax.swing.event.ChangeListener
import packetproxy.common.I18nString
import packetproxy.model.Packet
import packetproxy.util.Logging.errWithStackTrace

/**
 * リクエストとレスポンスを左右に並べて表示するパネル 各パネルにReceived Packet, Decoded, Modified, Encoded, Allのタブを持つ
 * HTTP以外の通信では単一パケット表示モードに切り替わる
 */
class GUIRequestResponsePanel(private val owner: JFrame) {
  private companion object {
    private const val SPLIT_PANE_DIVIDER_SIZE = 8
    private const val ALL_PANEL_ROWS = 1
    private const val ALL_PANEL_COLUMNS = 4
    private const val LABEL_ALIGNMENT_CENTER = 0.5f
    private const val MIN_PANEL_SIZE = 100
    private const val SPLIT_PANE_RESIZE_WEIGHT = 0.5
    private val REQUEST_BORDER_COLOR = Color(0x33, 0x99, 0xff)
    private val RESPONSE_BORDER_COLOR = Color(0x99, 0x33, 0x33)
    private val SINGLE_BORDER_COLOR = Color(0x66, 0x66, 0x99)
    private val EMPTY_DATA = ByteArray(0)
  }

  private enum class ViewType {
    SPLIT,
    SINGLE,
  }

  private enum class TabType(val index: Int) {
    RECEIVED(0),
    DECODED(1),
    MODIFIED(2),
    ENCODED(3),
    ALL(4);

    companion object {
      fun fromIndex(index: Int): TabType? {
        return values().firstOrNull { it.index == index }
      }
    }
  }

  private lateinit var mainPanel: JPanel
  private lateinit var cardLayout: CardLayout
  private lateinit var splitPane: JSplitPane

  private lateinit var buttonPanel: JPanel
  private lateinit var buttonCardLayout: CardLayout

  private lateinit var requestPane: PacketDetailPane
  private lateinit var responsePane: PacketDetailPane
  private lateinit var singlePane: PacketDetailPane

  // 現在表示中のパケット
  private var showingRequestPacket: Packet? = null
  private var showingResponsePacket: Packet? = null
  private var showingSinglePacket: Packet? = null
  private var currentView: ViewType = ViewType.SPLIT

  // Copy Body のデータ取得元（最後にクリックされたペイン）。null のときは requestPane を使う
  private var activePaneForBody: PacketDetailPane? = null

  @Throws(Exception::class)
  fun createPanel(): JComponent {
    cardLayout = CardLayout()
    mainPanel = JPanel(cardLayout)

    // === 分割ビュー（HTTP用）===
    requestPane = PacketDetailPane("Request", REQUEST_BORDER_COLOR, true)
    responsePane = PacketDetailPane("Response", RESPONSE_BORDER_COLOR, true)
    requestPane.addChangeListener(ChangeListener { updateRequestPanel() })
    responsePane.addChangeListener(ChangeListener { updateResponsePanel() })

    // 左右に分割
    splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPane.panel, responsePane.panel)
    splitPane.resizeWeight = SPLIT_PANE_RESIZE_WEIGHT
    splitPane.isContinuousLayout = true
    splitPane.dividerSize = SPLIT_PANE_DIVIDER_SIZE

    mainPanel.add(splitPane, ViewType.SPLIT.name)

    // === 単一パケットビュー（非HTTP用）===
    singlePane = PacketDetailPane("Streaming Packet", SINGLE_BORDER_COLOR, false)
    singlePane.addChangeListener(ChangeListener { updateSinglePacketPanel() })
    mainPanel.add(singlePane.panel, ViewType.SINGLE.name)

    // === 共有ボタンパネル（分割表示の対象外・下部に1つだけ表示）===
    buttonCardLayout = CardLayout()
    buttonPanel = JPanel(buttonCardLayout)
    buttonPanel.add(requestPane.receivedPanel.createButtonPanel(), ViewType.SPLIT.name)
    // ボタンが現在アクティブな外側タブ（Decoded/Modified等）のデータを読むようにサプライヤを注入する
    requestPane.receivedPanel.setDataProvider { requestPane.getActiveData() }
    buttonPanel.add(singlePane.receivedPanel.createButtonPanel(), ViewType.SINGLE.name)
    singlePane.receivedPanel.setDataProvider { singlePane.getActiveData() }

    requestPane.receivedPanel.setBodyDataProvider { getBodyData() }
    requestPane.receivedPanel.setResponseDataProvider { responsePane.getActiveData() }

    registerBodyFocusTracker()

    val wrapper = JPanel(BorderLayout())
    wrapper.add(mainPanel, BorderLayout.CENTER)
    wrapper.add(buttonPanel, BorderLayout.SOUTH)
    return wrapper
  }

  private fun getBodyData(): ByteArray = (activePaneForBody ?: requestPane).getActiveData()

  private fun registerBodyFocusTracker() {
    Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
      if (event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED) {
        val source = event.source as? Component ?: return@addAWTEventListener
        when {
          SwingUtilities.isDescendingFrom(source, requestPane.panel) -> activePaneForBody = requestPane
          SwingUtilities.isDescendingFrom(source, responsePane.panel) -> activePaneForBody = responsePane
        }
      }
    }, AWTEvent.MOUSE_EVENT_MASK)
  }

  private inner class PacketDetailPane(
    private val title: String,
    private val borderColor: Color,
    private val shouldSetMinimumSize: Boolean,
  ) {
    val panel: JPanel = JPanel()
    private val tabs = JTabbedPane()
    private val decodedTabs = TabSet(true, false)
    val receivedPanel = GUIData(owner)
    private val modifiedPanel = GUIData(owner)
    private val sentPanel = GUIData(owner)
    private lateinit var allReceived: RawTextPane
    private lateinit var allDecoded: RawTextPane
    private lateinit var allModified: RawTextPane
    private lateinit var allSent: RawTextPane

    init {
      panel.layout = BorderLayout()
      panel.border =
        BorderFactory.createTitledBorder(
          BorderFactory.createLineBorder(borderColor, 2),
          title,
          TitledBorder.LEFT,
          TitledBorder.TOP,
          null,
          borderColor,
        )
      if (shouldSetMinimumSize) {
        panel.minimumSize = Dimension(MIN_PANEL_SIZE, MIN_PANEL_SIZE)
      }

      tabs.addTab("Received Packet", receivedPanel.createTabsPanel())
      tabs.addTab("Decoded", decodedTabs.tabPanel)
      tabs.addTab("Modified", modifiedPanel.createTabsPanel())
      tabs.addTab("Encoded (Sent Packet)", sentPanel.createTabsPanel())
      tabs.addTab("All", createAllPanel())
      tabs.selectedIndex = TabType.DECODED.index

      panel.add(tabs, BorderLayout.CENTER)
    }

    fun addChangeListener(listener: ChangeListener) {
      tabs.addChangeListener(listener)
    }

    fun update(packet: Packet?) {
      if (packet == null) {
        clear()
        return
      }
      try {
        when (TabType.fromIndex(tabs.selectedIndex)) {
          TabType.DECODED -> decodedTabs.setData(resolveDecodedData(packet))
          TabType.RECEIVED -> receivedPanel.setData(packet.getReceivedData())
          TabType.MODIFIED -> modifiedPanel.setData(packet.getModifiedData())
          TabType.ENCODED -> sentPanel.setData(packet.getSentData())
          TabType.ALL -> {
            allReceived.setData(packet.getReceivedData(), true)
            allReceived.caretPosition = 0
            allDecoded.setData(packet.getDecodedData(), true)
            allDecoded.caretPosition = 0
            allModified.setData(packet.getModifiedData(), true)
            allModified.caretPosition = 0
            allSent.setData(packet.getSentData(), true)
            allSent.caretPosition = 0
          }
          null -> error("Unknown tab index: ${tabs.selectedIndex}")
        }
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }

    fun clear() {
      try {
        decodedTabs.setData(EMPTY_DATA)
        receivedPanel.setData(EMPTY_DATA)
        modifiedPanel.setData(EMPTY_DATA)
        sentPanel.setData(EMPTY_DATA)
        allReceived.setData(EMPTY_DATA, true)
        allDecoded.setData(EMPTY_DATA, true)
        allModified.setData(EMPTY_DATA, true)
        allSent.setData(EMPTY_DATA, true)
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }

    fun getDecodedData(): ByteArray {
      return decodedTabs.getData()
    }

    fun getActiveData(): ByteArray {
      return when (TabType.fromIndex(tabs.selectedIndex)) {
        TabType.RECEIVED -> receivedPanel.getData()
        TabType.DECODED -> decodedTabs.getData()
        TabType.MODIFIED -> modifiedPanel.getData()
        TabType.ENCODED -> sentPanel.getData()
        TabType.ALL -> decodedTabs.getData()
        null -> EMPTY_DATA
      }
    }

    private fun createAllPanel(): JComponent {
      val panel = JPanel()
      panel.layout = GridLayout(ALL_PANEL_ROWS, ALL_PANEL_COLUMNS)

      allReceived = createTextPaneForAll(panel, I18nString.get("Received"))
      allDecoded = createTextPaneForAll(panel, I18nString.get("Decoded"))
      allModified = createTextPaneForAll(panel, I18nString.get("Modified"))
      allSent = createTextPaneForAll(panel, I18nString.get("Encoded"))

      return panel
    }
  }

  @Throws(Exception::class)
  private fun createTextPaneForAll(parentPanel: JPanel, labelName: String): RawTextPane {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

    val label = JLabel(labelName)
    label.alignmentX = LABEL_ALIGNMENT_CENTER

    val text = RawTextPane()
    text.isEditable = false
    panel.add(label)
    val scroll = JScrollPane(text)
    scroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    panel.add(scroll)
    parentPanel.add(panel)
    return text
  }

  fun setRequestPacket(packet: Packet) {
    showingRequestPacket = packet
    updateRequestPanel()
  }

  fun setResponsePacket(packet: Packet) {
    showingResponsePacket = packet
    updateResponsePanel()
  }

  /** 単一パケット表示モード用：パケットを設定 Streaming通信で使用 */
  fun setSinglePacket(packet: Packet) {
    activePaneForBody = null
    showingSinglePacket = packet
    switchToSingleView()
    updateSinglePacketPanel()
  }

  /** リクエスト/レスポンス分割表示モード用：両方のパケットを設定 HTTP通信で使用 */
  fun setPackets(requestPacket: Packet, responsePacket: Packet?) {
    activePaneForBody = null
    showingRequestPacket = requestPacket
    showingResponsePacket = responsePacket
    switchToSplitView()
    updateRequestPanel()
    updateResponsePanel()
  }

  private fun switchToSplitView() {
    if (currentView != ViewType.SPLIT) {
      currentView = ViewType.SPLIT
      cardLayout.show(mainPanel, ViewType.SPLIT.name)
      buttonCardLayout.show(buttonPanel, ViewType.SPLIT.name)
    }
  }

  private fun switchToSingleView() {
    if (currentView != ViewType.SINGLE) {
      currentView = ViewType.SINGLE
      cardLayout.show(mainPanel, ViewType.SINGLE.name)
      buttonCardLayout.show(buttonPanel, ViewType.SINGLE.name)
    }
  }

  private fun resolveDecodedData(packet: Packet): ByteArray {
    var decodedData = packet.getDecodedData()
    if (decodedData == null || decodedData.isEmpty()) {
      decodedData = packet.getModifiedData()
    }
    return decodedData ?: EMPTY_DATA
  }

  private fun updateRequestPanel() {
    requestPane.update(showingRequestPacket)
  }

  private fun updateResponsePanel() {
    responsePane.update(showingResponsePacket)
  }

  private fun updateSinglePacketPanel() {
    singlePane.update(showingSinglePacket)
  }

  fun getRequestData(): ByteArray {
    if (showingRequestPacket == null) return EMPTY_DATA
    // Decodedタブからデータを取得
    return requestPane.getDecodedData()
  }

  fun getResponseData(): ByteArray {
    if (showingResponsePacket == null) return EMPTY_DATA
    // Decodedタブからデータを取得
    return responsePane.getDecodedData()
  }
}
