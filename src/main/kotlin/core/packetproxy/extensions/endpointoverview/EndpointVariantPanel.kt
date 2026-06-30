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
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import javax.swing.table.DefaultTableModel
import packetproxy.common.I18nString

class EndpointVariantPanel : JPanel(BorderLayout()) {
  private val tableModel = DefaultTableModel()
  private val table =
    JTable(tableModel).apply {
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      autoCreateRowSorter = false
    }
  private var rows: List<EndpointVariantRow> = emptyList()
  private var onVariantSelected: ((EndpointSummary) -> Unit)? = null
  private var suppressSelectionEvent = false

  init {
    add(JLabel(I18nString.get("Variants")), BorderLayout.NORTH)
    add(JScrollPane(table), BorderLayout.CENTER)
    table.selectionModel.addListSelectionListener(
      ListSelectionListener { event ->
        if (event.valueIsAdjusting || suppressSelectionEvent) {
          return@ListSelectionListener
        }
        notifySelection()
      }
    )
  }

  fun setOnVariantSelected(listener: (EndpointSummary) -> Unit) {
    onVariantSelected = listener
  }

  fun clear() {
    rows = emptyList()
    tableModel.setDataVector(emptyArray(), emptyArray<String>())
  }

  fun setVariants(variants: List<EndpointSummary>) {
    rows = EndpointVariantExtractor.buildRows(variants)
    val columns = EndpointVariantExtractor.differingColumns(rows)
    val data =
      rows
        .map { row -> columns.map { column -> row.fields[column] ?: "" }.toTypedArray() }
        .toTypedArray()
    suppressSelectionEvent = true
    try {
      tableModel.setDataVector(data, columns.toTypedArray())
      if (rows.isNotEmpty()) {
        table.selectionModel.setSelectionInterval(0, 0)
        notifySelection()
      }
    } finally {
      suppressSelectionEvent = false
    }
  }

  fun getSelectedSummary(): EndpointSummary? {
    val index = table.selectedRow
    if (index < 0 || index >= rows.size) {
      return null
    }
    return rows[index].summary
  }

  private fun notifySelection() {
    val summary = getSelectedSummary() ?: return
    onVariantSelected?.invoke(summary)
  }
}
