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
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import packetproxy.extensions.securityheaders.exclusion.ExclusionRule
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleManager
import packetproxy.extensions.securityheaders.exclusion.ExclusionRuleType

/**
 * Dialog for managing exclusion rules. Provides UI for adding, editing, and deleting exclusion
 * rules.
 */
class ExclusionManagementDialog(
  parent: Frame,
  private val exclusionRuleManager: ExclusionRuleManager,
) {
  private val dialog: JDialog
  private val exclusionTableModel: DefaultTableModel
  private val exclusionTable: JTable

  init {
    dialog =
      JDialog(parent, "Manage Exclusions", true).apply {
        layout = BorderLayout()
        setSize(600, 400)
        setLocationRelativeTo(parent)
      }

    val columnNames = arrayOf("Type", "Pattern")
    exclusionTableModel =
      object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
      }

    refreshTable()

    exclusionTable =
      JTable(exclusionTableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        columnModel.getColumn(0).preferredWidth = 80
        columnModel.getColumn(1).preferredWidth = 400
      }

    val scrollPane = JScrollPane(exclusionTable)
    dialog.add(scrollPane, BorderLayout.CENTER)

    val buttonPanel = createButtonPanel()
    dialog.add(buttonPanel, BorderLayout.SOUTH)
  }

  private fun createButtonPanel(): JPanel {
    val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))

    val addButton = JButton("Add").apply { addActionListener { handleAdd() } }
    buttonPanel.add(addButton)

    val editButton = JButton("Edit").apply { addActionListener { handleEdit() } }
    buttonPanel.add(editButton)

    val deleteButton = JButton("Delete").apply { addActionListener { handleDelete() } }
    buttonPanel.add(deleteButton)

    val clearAllButton = JButton("Clear All").apply { addActionListener { handleClearAll() } }
    buttonPanel.add(clearAllButton)

    val closeButton = JButton("Close").apply { addActionListener { dialog.dispose() } }
    buttonPanel.add(closeButton)

    return buttonPanel
  }

  private fun handleAdd() {
    val newRule = showEditDialog(null)
    if (newRule != null) {
      exclusionRuleManager.addRule(newRule)
      refreshTable()
    }
  }

  private fun handleEdit() {
    val selectedRow = exclusionTable.selectedRow
    if (selectedRow == -1) {
      JOptionPane.showMessageDialog(
        dialog,
        "Please select a rule to edit.",
        "No Selection",
        JOptionPane.WARNING_MESSAGE,
      )
      return
    }

    val rules = exclusionRuleManager.getRules()
    if (selectedRow < rules.size) {
      val oldRule = rules[selectedRow]
      val editedRule = showEditDialog(oldRule)
      if (editedRule != null) {
        exclusionRuleManager.updateRule(oldRule.id, editedRule.type, editedRule.pattern)
        refreshTable()
      }
    }
  }

  private fun handleDelete() {
    val selectedRow = exclusionTable.selectedRow
    if (selectedRow == -1) {
      JOptionPane.showMessageDialog(
        dialog,
        "Please select a rule to delete.",
        "No Selection",
        JOptionPane.WARNING_MESSAGE,
      )
      return
    }

    val rules = exclusionRuleManager.getRules()
    if (selectedRow < rules.size) {
      val rule = rules[selectedRow]
      exclusionRuleManager.removeRule(rule.id)
      refreshTable()
    }
  }

  private fun handleClearAll() {
    val confirm =
      JOptionPane.showConfirmDialog(
        dialog,
        "Are you sure you want to delete all exclusion rules?",
        "Confirm Clear All",
        JOptionPane.YES_NO_OPTION,
      )
    if (confirm == JOptionPane.YES_OPTION) {
      exclusionRuleManager.clearRules()
      refreshTable()
    }
  }

  private fun refreshTable() {
    exclusionTableModel.rowCount = 0
    for (rule in exclusionRuleManager.getRules()) {
      exclusionTableModel.addRow(arrayOf(rule.type.displayName, rule.pattern))
    }
  }

  private fun showEditDialog(existingRule: ExclusionRule?): ExclusionRule? {
    val panel = JPanel(GridBagLayout())
    val gbc =
      GridBagConstraints().apply {
        insets = Insets(5, 5, 5, 5)
        anchor = GridBagConstraints.WEST
      }

    gbc.gridx = 0
    gbc.gridy = 0
    panel.add(JLabel("Type:"), gbc)

    val typeCombo = JComboBox(ExclusionRuleType.values())
    gbc.gridx = 1
    gbc.fill = GridBagConstraints.HORIZONTAL
    panel.add(typeCombo, gbc)

    gbc.gridx = 0
    gbc.gridy = 1
    gbc.fill = GridBagConstraints.NONE
    panel.add(JLabel("Pattern:"), gbc)

    val patternField = JTextField(30)
    gbc.gridx = 1
    gbc.fill = GridBagConstraints.HORIZONTAL
    panel.add(patternField, gbc)

    gbc.gridx = 0
    gbc.gridy = 2
    gbc.gridwidth = 2
    val hintLabel =
      JLabel("<html><i>Host: example.com | Path: /api/* | Endpoint: GET https://...</i></html>")
        .apply { foreground = Color.GRAY }
    panel.add(hintLabel, gbc)

    if (existingRule != null) {
      typeCombo.selectedItem = existingRule.type
      patternField.text = existingRule.pattern
    }

    val title = if (existingRule == null) "Add Exclusion Rule" else "Edit Exclusion Rule"
    val result =
      JOptionPane.showConfirmDialog(
        dialog,
        panel,
        title,
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE,
      )

    if (result == JOptionPane.OK_OPTION) {
      val pattern = patternField.text.trim()
      if (pattern.isNotEmpty()) {
        val type = typeCombo.selectedItem as ExclusionRuleType
        return if (existingRule != null) {
          ExclusionRule(existingRule.id, type, pattern)
        } else {
          ExclusionRule(type, pattern)
        }
      }
    }

    return null
  }

  fun show() {
    refreshTable()
    dialog.isVisible = true
  }
}
