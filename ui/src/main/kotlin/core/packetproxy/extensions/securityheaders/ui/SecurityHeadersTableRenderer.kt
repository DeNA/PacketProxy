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
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import packetproxy.extensions.securityheaders.SecurityCheck
import packetproxy.extensions.securityheaders.SecurityCheckResult

/**
 * Custom table renderers for security headers extension. Provides header renderer and cell renderer
 * for security check results.
 */
object SecurityHeadersTableRenderer {
  const val FIXED_COLUMNS = 3 // Method, URL, Code
  private val COLOR_FAIL = Color(200, 0, 0)
  private val COLOR_WARN = Color(220, 130, 0)
  private val COLOR_OK = Color(0, 100, 0)
  private val COLOR_FAIL_BG = Color(255, 240, 240)
  private val COLOR_WARN_BG = Color(255, 250, 230)

  /** Custom header renderer: left-aligned text with sort icon on the right */
  class HeaderRenderer(table: JTable) : JPanel(BorderLayout()), TableCellRenderer {
    private val textLabel: JLabel
    private val iconLabel: JLabel
    private val defaultRenderer: TableCellRenderer

    init {
      defaultRenderer = table.tableHeader.defaultRenderer
      isOpaque = true

      textLabel = JLabel().apply { horizontalAlignment = SwingConstants.LEFT }

      iconLabel = JLabel().apply { horizontalAlignment = SwingConstants.RIGHT }

      add(textLabel, BorderLayout.CENTER)
      add(iconLabel, BorderLayout.EAST)
    }

    override fun getTableCellRendererComponent(
      table: JTable,
      value: Any?,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int,
    ): Component {
      // Get default component to extract styling
      val defaultComponent =
        defaultRenderer.getTableCellRendererComponent(
          table,
          value,
          isSelected,
          hasFocus,
          row,
          column,
        )

      // Copy background and border from default renderer
      background = defaultComponent.background
      foreground = defaultComponent.foreground
      font = defaultComponent.font
      if (defaultComponent is JComponent) {
        border = defaultComponent.border
      }

      // Set text
      textLabel.text = value?.toString() ?: ""
      textLabel.font = font
      textLabel.foreground = foreground

      // Get sort icon from default renderer
      iconLabel.icon = null
      if (defaultComponent is JLabel) {
        iconLabel.icon = defaultComponent.icon
      }

      return this
    }
  }

  /** Cell renderer for security check results with color coding */
  class SecurityHeaderRenderer(
    private val table: JTable,
    private val model: DefaultTableModel,
    private val securityChecks: List<SecurityCheck>,
    private val resultsMap: Map<String, Map<String, SecurityCheckResult>>,
  ) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
      table: JTable,
      value: Any?,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int,
    ): Component {
      val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

      val modelRow = table.convertRowIndexToModel(row)
      val endpointKey = buildEndpointKey(modelRow)
      val results = resultsMap[endpointKey]

      applyBackgroundColor(c, results, isSelected)
      applyForegroundColor(c, column, results, isSelected)

      return c
    }

    private fun buildEndpointKey(modelRow: Int): String {
      val method = model.getValueAt(modelRow, 0) as String
      val url = model.getValueAt(modelRow, 1) as String
      val code = model.getValueAt(modelRow, 2) as String
      return "$method $url $code"
    }

    private fun applyBackgroundColor(
      c: Component,
      results: Map<String, SecurityCheckResult>?,
      isSelected: Boolean,
    ) {
      if (isSelected) return

      if (results == null) {
        c.background = Color.WHITE
        return
      }

      val hasFail = results.values.any { it.isFail }
      val hasWarn = results.values.any { it.isWarn }

      c.background =
        when {
          hasFail -> COLOR_FAIL_BG
          hasWarn -> COLOR_WARN_BG
          else -> Color.WHITE
        }
    }

    private fun applyForegroundColor(
      c: Component,
      column: Int,
      results: Map<String, SecurityCheckResult>?,
      isSelected: Boolean,
    ) {
      if (isSelected) {
        c.foreground = table.selectionForeground
        return
      }

      if (column < FIXED_COLUMNS) {
        c.foreground = Color.BLACK
        return
      }

      val checkIndex = column - FIXED_COLUMNS
      if (checkIndex < securityChecks.size && results != null) {
        val check = securityChecks[checkIndex]
        val result = results[check.name]
        applyResultStyle(c, result)
      } else {
        c.foreground = Color.BLACK
      }
    }

    private fun applyResultStyle(c: Component, result: SecurityCheckResult?) {
      if (result == null) {
        c.foreground = Color.BLACK
        return
      }

      when {
        result.isFail -> {
          c.foreground = COLOR_FAIL
          c.font = c.font.deriveFont(Font.BOLD)
        }
        result.isWarn -> {
          c.foreground = COLOR_WARN
          c.font = c.font.deriveFont(Font.BOLD)
        }
        result.isOk -> {
          c.foreground = COLOR_OK
        }
        else -> {
          c.foreground = Color.BLACK
        }
      }
    }
  }
}
