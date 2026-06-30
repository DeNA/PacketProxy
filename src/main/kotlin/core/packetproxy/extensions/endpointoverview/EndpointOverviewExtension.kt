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
package packetproxy.extensions.endpointoverview

import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.beans.PropertyChangeListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import packetproxy.common.I18nString
import packetproxy.controller.ResendController
import packetproxy.controller.ResendController.ResendWorker
import packetproxy.gui.GUIHistory
import packetproxy.gui.GUIOptionSessionProfileDialog
import packetproxy.gui.GUIRequestResponsePanel
import packetproxy.http.SessionProfileAuthorizationExtractor
import packetproxy.http.SessionRequestModifier
import packetproxy.model.Extension
import packetproxy.model.OneShotPacket
import packetproxy.model.Packets
import packetproxy.model.SessionProfile
import packetproxy.model.SessionProfiles
import packetproxy.util.Logging.errWithStackTrace

class EndpointOverviewExtension : Extension() {
  private data class SessionComboEntry(val label: String, val profile: SessionProfile?) {
    override fun toString(): String = label
  }

  private lateinit var tree: JTree
  private lateinit var treeModel: DefaultTreeModel
  private lateinit var filterField: JTextField
  private lateinit var requestResponsePanel: GUIRequestResponsePanel
  private lateinit var sessionComboBox: JComboBox<SessionComboEntry>
  private lateinit var sendButton: JButton
  private var endpoints: Collection<EndpointSummary> = emptyList()
  private var sessionProfileListener: PropertyChangeListener? = null

  init {
    setName("EndpointOverview")
  }

  override fun createPanel(): JComponent {
    val panel = JPanel(BorderLayout())

    initializeTree()
    initializeSessionControls()
    setupSelectionListener()

    requestResponsePanel = GUIRequestResponsePanel(GUIHistory.getOwner())

    val treeScrollPane = JScrollPane(tree)
    val mainSplit =
      JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScrollPane, requestResponsePanel.createPanel())
    mainSplit.dividerLocation = 300

    panel.add(createToolbar(), BorderLayout.NORTH)
    panel.add(mainSplit, BorderLayout.CENTER)

    scanHistory()

    return panel
  }

  private fun initializeSessionControls() {
    sessionComboBox = JComboBox()
    sendButton = JButton(I18nString.get("Send"))
    sendButton.isEnabled = false

    try {
      refreshSessionComboBox()
      sessionProfileListener = PropertyChangeListener { refreshSessionComboBox() }
      SessionProfiles.getInstance().addPropertyChangeListener(sessionProfileListener)
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }

  private fun initializeTree() {
    treeModel = DefaultTreeModel(DefaultMutableTreeNode(EndpointTreeRoot()))
    tree = JTree(treeModel)
    tree.isRootVisible = true
    tree.showsRootHandles = true
    tree.cellRenderer =
      object : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
          tree: JTree,
          value: Any?,
          selected: Boolean,
          expanded: Boolean,
          leaf: Boolean,
          row: Int,
          hasFocus: Boolean,
        ): Component {
          super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
          val userObject = (value as? DefaultMutableTreeNode)?.userObject
          text =
            when (userObject) {
              is EndpointTreeNode -> userObject.displayName
              else -> userObject?.toString() ?: ""
            }
          return this
        }
      }
  }

  private fun setupSelectionListener() {
    tree.addTreeSelectionListener(
      TreeSelectionListener { event: TreeSelectionEvent ->
        if (event.isAddedPath()) {
          showSelectedEndpoint()
        }
        updateSendButtonState()
      }
    )
  }

  private fun showSelectedEndpoint() {
    val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
    val summary = resolveSummary(selectedNode) ?: return
    val requestPacket = summary.latestRequestPacket ?: return
    val responsePacket = summary.latestResponsePacket ?: return
    requestResponsePanel.setPackets(requestPacket, responsePacket)
  }

  private fun resolveSummary(node: DefaultMutableTreeNode): EndpointSummary? {
    return when (val userObject = node.userObject) {
      is EndpointTreeMethod -> userObject.summary
      else -> null
    }
  }

  private fun createToolbar(): JPanel {
    val toolbar = JPanel(BorderLayout())

    val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    val scanButton = JButton(I18nString.get("Scan History"))
    scanButton.addActionListener { scanHistory() }
    buttonPanel.add(scanButton)

    val clearButton = JButton(I18nString.get("Clear"))
    clearButton.addActionListener { clearTree() }
    buttonPanel.add(clearButton)

    buttonPanel.add(JLabel(I18nString.get("Session:")))
    buttonPanel.add(sessionComboBox)

    sendButton.addActionListener { resendWithSelectedSession() }
    buttonPanel.add(sendButton)

    val manageButton = JButton(I18nString.get("Manage..."))
    manageButton.addActionListener { openSessionManageDialog() }
    buttonPanel.add(manageButton)

    toolbar.add(buttonPanel, BorderLayout.WEST)

    val filterPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
    filterPanel.add(JLabel(I18nString.get("Filter:")))
    filterField = JTextField(20)
    filterField.document.addDocumentListener(
      object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) {
          applyFilter()
        }

        override fun removeUpdate(e: DocumentEvent) {
          applyFilter()
        }

        override fun changedUpdate(e: DocumentEvent) {
          applyFilter()
        }
      }
    )
    filterPanel.add(filterField)
    toolbar.add(filterPanel, BorderLayout.EAST)

    return toolbar
  }

  private fun refreshSessionComboBox() {
    val selectedProfile = (sessionComboBox.selectedItem as? SessionComboEntry)?.profile
    val entries = mutableListOf(SessionComboEntry(I18nString.get("(Original)"), null))
    try {
      SessionProfiles.getInstance().queryAll().forEach { profile ->
        entries.add(SessionComboEntry(profile.name, profile))
      }
    } catch (e: Exception) {
      errWithStackTrace(e)
    }

    sessionComboBox.model = DefaultComboBoxModel(entries.toTypedArray())
    if (selectedProfile != null) {
      entries
        .firstOrNull { it.profile?.id == selectedProfile.id }
        ?.let { sessionComboBox.selectedItem = it }
    }
  }

  private fun updateSendButtonState() {
    val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
    sendButton.isEnabled = selectedNode != null && resolveSummary(selectedNode) != null
  }

  private fun resendWithSelectedSession() {
    val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
    val summary = resolveSummary(selectedNode) ?: return
    val requestPacket = summary.latestRequestPacket ?: return
    val entry = sessionComboBox.selectedItem as? SessionComboEntry ?: return

    try {
      val requestBytes = requestResponsePanel.getActiveRequestData()
      val baseBytes = if (requestBytes.isNotEmpty()) requestBytes else requestPacket.decodedData
      val modifiedBytes = SessionRequestModifier.apply(baseBytes, entry.profile)
      val sendPacket = requestPacket.getOneShotPacket(modifiedBytes)
      val modifiedRequestPacket = sendPacket.toPacket()

      ResendController.getInstance()
        .resend(
          object : ResendWorker(sendPacket, 1) {
            override fun process(chunks: MutableList<OneShotPacket>) {
              if (chunks.isEmpty()) {
                return
              }
              SwingUtilities.invokeLater {
                try {
                  val responsePacket = chunks[0].toPacket()
                  requestResponsePanel.setPackets(modifiedRequestPacket, responsePacket)
                } catch (e: Exception) {
                  errWithStackTrace(e)
                }
              }
            }
          }
        )
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }

  private fun openSessionManageDialog() {
    try {
      val dlg =
        GUIOptionSessionProfileDialog(GUIHistory.getOwner()) {
          val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
          val requestData =
            selectedNode?.let { resolveSummary(it)?.latestRequestPacket?.decodedData }
          SessionProfileAuthorizationExtractor.extract(requestData ?: ByteArray(0))
        }
      dlg.showDialog()
    } catch (e: Exception) {
      errWithStackTrace(e)
    }
  }

  private fun applyFilter() {
    populateTree(endpoints)
  }

  private fun filterSummaries(
    summaries: Collection<EndpointSummary>,
    text: String,
  ): Collection<EndpointSummary> {
    if (text.isEmpty()) {
      return summaries
    }

    val lower = text.lowercase()
    return summaries.filter { summary ->
      summary.host.lowercase().contains(lower) ||
        summary.url.lowercase().contains(lower) ||
        summary.method.lowercase().contains(lower) ||
        summary.formattedStatusCodes().lowercase().contains(lower)
    }
  }

  private fun clearTree() {
    SwingUtilities.invokeLater {
      endpoints = emptyList()
      treeModel.setRoot(DefaultMutableTreeNode(EndpointTreeRoot()))
      filterField.text = ""
      updateSendButtonState()
    }
  }

  private fun scanHistory() {
    Thread {
        try {
          val aggregated = EndpointAggregator.aggregateEndpoints(Packets.getInstance().queryAll())
          SwingUtilities.invokeLater {
            endpoints = aggregated.values
            populateTree(endpoints)
          }
        } catch (e: Exception) {
          errWithStackTrace(e)
        }
      }
      .start()
  }

  private fun populateTree(summaries: Collection<EndpointSummary>) {
    val filtered = filterSummaries(summaries, filterField.text.trim())
    treeModel.setRoot(EndpointTreeBuilder.build(filtered))
    expandHostNodes()
  }

  private fun expandHostNodes() {
    val root = treeModel.root as? DefaultMutableTreeNode ?: return
    tree.expandRow(0)
    for (i in 0 until root.childCount) {
      tree.expandRow(i + 1)
    }
  }
}
