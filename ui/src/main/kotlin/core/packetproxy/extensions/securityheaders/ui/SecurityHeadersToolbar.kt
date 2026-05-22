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
package packetproxy.extensions.securityheaders.ui

import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.util.regex.Pattern
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.RowFilter
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleManager

/**
 * Toolbar component for security headers extension. Provides buttons and filter controls for the
 * main table.
 */
class SecurityHeadersToolbar(
  private val exclusionRuleManager: ExclusionRuleManager,
  private val onScanHistory: Runnable,
  private val onClearTable: Runnable,
) {
  companion object {
    private val METHOD_OPTIONS = arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")
    private val STATUS_CODE_OPTIONS = arrayOf("2xx", "3xx", "4xx", "5xx")
  }

  val panel: JPanel
  private val methodCheckBoxes = mutableListOf<JCheckBox>()
  private val statusCheckBoxes = mutableListOf<JCheckBox>()
  private lateinit var filterField: JTextField
  private var sorter: TableRowSorter<DefaultTableModel>? = null

  init {
    panel = createPanel()
  }

  fun setSorter(sorter: TableRowSorter<DefaultTableModel>) {
    this.sorter = sorter
  }

  fun showExclusionsDialog(parentFrame: Frame) {
    val dialog = ExclusionManagementDialog(parentFrame, exclusionRuleManager)
    dialog.show()
  }

  fun applyFilter() {
    val sorter = this.sorter ?: return
    if (methodCheckBoxes.isEmpty() || statusCheckBoxes.isEmpty() || !::filterField.isInitialized) {
      return
    }

    val filters = mutableListOf<RowFilter<DefaultTableModel, Any>>()

    // Method filter (OR between selected methods)
    val methodFilters = mutableListOf<RowFilter<DefaultTableModel, Any>>()
    for (cb in methodCheckBoxes) {
      if (cb.isSelected) {
        methodFilters.add(RowFilter.regexFilter("^${Pattern.quote(cb.text)}\$", 0))
      }
    }
    if (methodFilters.isNotEmpty()) {
      filters.add(RowFilter.orFilter(methodFilters))
    }

    // Status Code filter (OR between selected statuses)
    val statusFilters = mutableListOf<RowFilter<DefaultTableModel, Any>>()
    for (cb in statusCheckBoxes) {
      if (cb.isSelected) {
        val statusPrefix = cb.text.substring(0, 1) // "2", "3", "4", or "5"
        statusFilters.add(RowFilter.regexFilter("^$statusPrefix\\d{2}\$", 2))
      }
    }
    if (statusFilters.isNotEmpty()) {
      filters.add(RowFilter.orFilter(statusFilters))
    }

    // Text filter
    val text = filterField.text.trim()
    if (text.isNotEmpty()) {
      filters.add(RowFilter.regexFilter("(?i)${Pattern.quote(text)}"))
    }

    // Exclusion rules filter
    filters.add(createExclusionFilter())

    // Apply combined filter (AND between method group, status group, text, and exclusions)
    sorter.rowFilter =
      if (filters.isEmpty()) {
        null
      } else {
        RowFilter.andFilter(filters)
      }
  }

  fun resetFilters() {
    filterField.text = ""
    methodCheckBoxes.forEach { it.isSelected = true }
    statusCheckBoxes.forEach { it.isSelected = true }
    applyFilter()
  }

  private fun createExclusionFilter(): RowFilter<DefaultTableModel, Any> {
    return object : RowFilter<DefaultTableModel, Any>() {
      override fun include(entry: Entry<out DefaultTableModel, out Any>): Boolean {
        val method = entry.getValue(0) as String
        val url = entry.getValue(1) as String
        // Return true to include (NOT excluded), false to exclude
        return !exclusionRuleManager.shouldExclude(method, url)
      }
    }
  }

  private fun createPanel(): JPanel {
    val buttonPanel = JPanel(BorderLayout())

    // Left side: buttons
    val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    val scanButton = JButton("Scan History").apply { addActionListener { onScanHistory.run() } }
    leftPanel.add(scanButton)

    val clearButton = JButton("Clear").apply { addActionListener { onClearTable.run() } }
    leftPanel.add(clearButton)

    val exclusionsButton =
      JButton("Exclusions").apply {
        addActionListener {
          val parentFrame = SwingUtilities.getWindowAncestor(panel) as? Frame
          if (parentFrame != null) {
            showExclusionsDialog(parentFrame)
          }
        }
      }
    leftPanel.add(exclusionsButton)

    buttonPanel.add(leftPanel, BorderLayout.WEST)

    // Right side: filter
    val filterPanel = JPanel(FlowLayout(FlowLayout.RIGHT))

    // Method filter checkboxes
    filterPanel.add(JLabel("Method:"))
    for (method in METHOD_OPTIONS) {
      val cb = JCheckBox(method, true) // default selected
      cb.addActionListener { applyFilter() }
      methodCheckBoxes.add(cb)
      filterPanel.add(cb)
    }

    filterPanel.add(Box.createHorizontalStrut(10))

    // Status Code filter checkboxes
    filterPanel.add(JLabel("Server Response:"))
    for (status in STATUS_CODE_OPTIONS) {
      val cb = JCheckBox(status, true) // default selected
      cb.addActionListener { applyFilter() }
      statusCheckBoxes.add(cb)
      filterPanel.add(cb)
    }

    filterPanel.add(Box.createHorizontalStrut(10))

    filterPanel.add(JLabel("Filter:"))
    filterField = JTextField(15)
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
    buttonPanel.add(filterPanel, BorderLayout.EAST)

    return buttonPanel
  }
}
