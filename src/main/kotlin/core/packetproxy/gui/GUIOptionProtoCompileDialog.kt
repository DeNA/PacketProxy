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

import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel
import packetproxy.common.I18nString
import packetproxy.grpc.GrpcServiceRegistryStore
import packetproxy.grpc.ProtoFileSet
import packetproxy.grpc.ProtocRunner

class GUIOptionProtoCompileDialog(owner: JFrame?, private val serverId: Int?) :
  JDialog(owner, I18nString.get("Generate .desc from .proto"), true) {

  private val protoSet = ProtoFileSet()
  private val tableModel =
    object : DefaultTableModel(arrayOf("Path"), 0) {
      override fun isCellEditable(row: Int, column: Int) = false
    }
  private var resultPath: String? = null

  init {
    val root = JPanel(BorderLayout(8, 8))
    root.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

    val top = JPanel()
    top.layout = BoxLayout(top, BoxLayout.X_AXIS)
    val addFile = JButton(I18nString.get("Add .proto file..."))
    addFile.addActionListener { addProtoFiles() }
    val addDir = JButton(I18nString.get("Add directory (1 level)..."))
    addDir.addActionListener { addProtoDirectory() }
    top.add(addFile)
    top.add(addDir)
    root.add(top, BorderLayout.NORTH)

    val table = JTable(tableModel)
    table.preferredScrollableViewportSize = Dimension(520, 200)
    root.add(JScrollPane(table), BorderLayout.CENTER)

    val remove = JButton(I18nString.get("Remove selected"))
    remove.addActionListener {
      val row = table.selectedRow
      if (row >= 0) {
        try {
          val f = File(tableModel.getValueAt(row, 0) as String)
          protoSet.remove(f)
          tableModel.removeRow(row)
        } catch (ex: Exception) {
          JOptionPane.showMessageDialog(
            this,
            ex.message,
            I18nString.get("Error"),
            JOptionPane.ERROR_MESSAGE,
          )
        }
      }
    }

    val bottom = JPanel()
    bottom.layout = BoxLayout(bottom, BoxLayout.Y_AXIS)
    bottom.add(remove)

    val actions = JPanel()
    actions.layout = BoxLayout(actions, BoxLayout.X_AXIS)
    val generate = JButton(I18nString.get("Generate .desc"))
    generate.addActionListener { runGenerate() }
    val cancel = JButton(I18nString.get("Cancel"))
    cancel.addActionListener {
      resultPath = null
      dispose()
    }
    actions.add(generate)
    actions.add(cancel)
    bottom.add(actions)
    root.add(bottom, BorderLayout.SOUTH)

    contentPane = root
    pack()
    setLocationRelativeTo(owner)
  }

  private fun addProtoFiles() {
    try {
      val chooser = NativeFileChooser()
      chooser.setDialogTitle(I18nString.get("Select .proto files"))
      chooser.addChoosableFileFilter(FileNameExtensionFilter("Protocol Buffers (*.proto)", "proto"))
      chooser.setAcceptAllFileFilterUsed(false)
      if (chooser.showOpenDialog(this) == NativeFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile
        if (f != null && protoSet.addFile(f)) {
          tableModel.addRow(arrayOf(f.absolutePath))
        }
      }
    } catch (ex: Exception) {
      JOptionPane.showMessageDialog(
        this,
        ex.message,
        I18nString.get("Error"),
        JOptionPane.ERROR_MESSAGE,
      )
    }
  }

  private fun addProtoDirectory() {
    try {
      val chooser = NativeFileChooser()
      chooser.setDialogTitle(I18nString.get("Select directory"))
      if (chooser.showDirectoryDialog(this) == NativeFileChooser.APPROVE_OPTION) {
        val dir = chooser.selectedFile
        if (dir != null && dir.isDirectory) {
          val n = protoSet.addDirectoryShallow(dir)
          if (n == 0) {
            JOptionPane.showMessageDialog(
              this,
              I18nString.get("No .proto files found in the selected directory."),
              I18nString.get("Info"),
              JOptionPane.INFORMATION_MESSAGE,
            )
          } else {
            refreshTableFromSet()
          }
        }
      }
    } catch (ex: Exception) {
      JOptionPane.showMessageDialog(
        this,
        ex.message,
        I18nString.get("Error"),
        JOptionPane.ERROR_MESSAGE,
      )
    }
  }

  @Throws(Exception::class)
  private fun refreshTableFromSet() {
    tableModel.rowCount = 0
    for (f in protoSet.list()) {
      tableModel.addRow(arrayOf(f.absolutePath))
    }
  }

  private fun runGenerate() {
    val protos = protoSet.list()
    if (protos.isEmpty()) {
      JOptionPane.showMessageDialog(
        this,
        I18nString.get("Add at least one .proto file."),
        I18nString.get("Error"),
        JOptionPane.WARNING_MESSAGE,
      )
      return
    }
    val worker =
      object : SwingWorker<Void?, Void?>() {
        @Throws(Exception::class)
        override fun doInBackground(): Void? {
          ProtocRunner.checkProtocOnPath()
          val includes = protoSet.includePaths()
          val r = ProtocRunner.run(protos, includes, serverId)
          if (!r.ok) {
            throw Exception(if (r.stderr.isEmpty()) "exit ${r.exitCode}" else r.stderr)
          }
          GrpcServiceRegistryStore.getInstance().invalidate(r.descFile)
          resultPath = r.descFile.absolutePath
          return null
        }

        override fun done() {
          try {
            get()
            SwingUtilities.invokeLater { dispose() }
          } catch (e: Exception) {
            val c = e.cause ?: e
            JOptionPane.showMessageDialog(
              this@GUIOptionProtoCompileDialog,
              c.message,
              I18nString.get("protoc failed"),
              JOptionPane.ERROR_MESSAGE,
            )
          }
        }
      }
    worker.execute()
  }

  /** Opens modally; returns absolute path to generated `.desc`, or `null` if cancelled. */
  fun showCompileDialog(): String? {
    resultPath = null
    isVisible = true
    return resultPath
  }
}
