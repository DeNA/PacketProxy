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

import java.awt.Color
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextPane
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyledDocument
import packetproxy.common.FontManager
import packetproxy.extensions.securityheaders.SecurityCheck
import packetproxy.extensions.securityheaders.SecurityCheckResult
import packetproxy.extensions.securityheaders.checks.CookieCheck
import packetproxy.gui.WrapEditorKit
import packetproxy.http.HttpHeader

/**
 * Detail panel for displaying HTTP headers and security check results. Provides styled text display
 * with color coding for security check results.
 */
class SecurityHeadersDetailPanel(private val securityChecks: List<SecurityCheck>) {
  private val headerPane: JTextPane
  private val detailArea: JTextPane
  private val textStyles: TextStyles

  init {
    textStyles = TextStyles()

    headerPane = createWrappedTextPane()
    detailArea = createWrappedTextPane()
  }

  fun createPanel(): JSplitPane {
    val headerScrollPane = JScrollPane(headerPane)
    val detailScrollPane = JScrollPane(detailArea)

    val bottomSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, headerScrollPane, detailScrollPane)
    bottomSplit.resizeWeight = 0.5

    return bottomSplit
  }

  fun populateHeaders(header: HttpHeader, results: Map<String, SecurityCheckResult>) {
    try {
      val doc = headerPane.styledDocument
      headerPane.text = ""

      doc.insertString(doc.length, "${header.statusline}\n", textStyles.bold)

      val headerBytes = header.toByteArray()
      val rawHeaders = String(headerBytes, Charsets.UTF_8)
      val lines = rawHeaders.split("\r\n", "\n")

      for (line in lines) {
        if (line.isEmpty()) continue

        val allSegments = collectHighlightSegments(line, results)

        if (allSegments.isNotEmpty()) {
          val sortedSegments = allSegments.sortedBy { it.start }
          insertLineWithSegments(doc, line, sortedSegments)
        } else {
          val style = getStyleForHeaderLine(line, results)
          doc.insertString(doc.length, "$line\n", style)
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun populateIssues(results: Map<String, SecurityCheckResult>) {
    try {
      val doc = detailArea.styledDocument
      detailArea.text = ""

      doc.insertString(doc.length, "Security Check Results\n", textStyles.bold)
      doc.insertString(doc.length, "=".repeat(40) + "\n\n", textStyles.black)

      for (check in securityChecks) {
        val result = results[check.name]
        if (result != null) {
          writeCheckResult(doc, check, result)
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun createWrappedTextPane(): JTextPane {
    return JTextPane().apply {
      isEditable = false
      background = Color.WHITE
      setEditorKit(WrapEditorKit(byteArrayOf()))
      font = FontManager.getInstance().font
    }
  }

  private fun collectHighlightSegments(
    line: String,
    results: Map<String, SecurityCheckResult>,
  ): List<SecurityCheck.HighlightSegment> {
    val allSegments = mutableListOf<SecurityCheck.HighlightSegment>()

    for (check in securityChecks) {
      if (!check.matchesHeaderLine(line.lowercase())) continue

      val result = results[check.name]
      val segments = check.getHighlightSegments(line, result)
      allSegments.addAll(segments)
    }

    return allSegments
  }

  private fun insertLineWithSegments(
    doc: StyledDocument,
    line: String,
    segments: List<SecurityCheck.HighlightSegment>,
  ) {
    var currentPos = 0
    val lineLength = line.length

    for (segment in segments) {
      val start = segment.start
      val end = segment.end

      if (start < 0 || end < 0 || start > lineLength || end > lineLength || start > end) {
        continue
      }

      if (start > currentPos) {
        val beforeText = line.substring(currentPos, start)
        doc.insertString(doc.length, beforeText, textStyles.black)
      }

      val segmentText = line.substring(start, end)
      val style = getStyleForHighlightType(segment.type)
      doc.insertString(doc.length, segmentText, style)
      currentPos = end
    }

    if (currentPos < line.length) {
      doc.insertString(doc.length, line.substring(currentPos), textStyles.black)
    }

    doc.insertString(doc.length, "\n", textStyles.black)
  }

  private fun getStyleForHighlightType(type: SecurityCheck.HighlightType): SimpleAttributeSet {
    return when (type) {
      SecurityCheck.HighlightType.GREEN -> textStyles.green
      SecurityCheck.HighlightType.RED -> textStyles.red
      SecurityCheck.HighlightType.YELLOW -> textStyles.yellow
      SecurityCheck.HighlightType.NONE -> textStyles.black
    }
  }

  private fun getStyleForHeaderLine(
    line: String,
    results: Map<String, SecurityCheckResult>,
  ): SimpleAttributeSet {
    for (check in securityChecks) {
      val result = results[check.name]
      val type = check.getHighlightType(line, result)
      when (type) {
        SecurityCheck.HighlightType.GREEN -> return textStyles.green
        SecurityCheck.HighlightType.RED -> return textStyles.red
        else -> {}
      }
    }

    val lowerLine = line.lowercase()
    if (lowerLine.startsWith("set-cookie:")) {
      return if (CookieCheck.hasSecureFlag(lowerLine)) textStyles.green else textStyles.red
    }

    return textStyles.black
  }

  private fun writeCheckResult(
    doc: StyledDocument,
    check: SecurityCheck,
    result: SecurityCheckResult,
  ) {
    doc.insertString(doc.length, "${check.name}: ", textStyles.bold)

    when {
      result.isOk -> {
        doc.insertString(doc.length, "OK\n", textStyles.green)
        doc.insertString(doc.length, "  ${result.displayValue}\n\n", textStyles.black)
      }
      result.isWarn -> {
        doc.insertString(doc.length, "WARNING\n", textStyles.yellow)
        doc.insertString(doc.length, "  ${check.warnMessage}\n", textStyles.yellow)
        doc.insertString(doc.length, "  Current: ${result.displayValue}\n\n", textStyles.black)
      }
      else -> {
        doc.insertString(doc.length, "FAIL\n", textStyles.red)
        doc.insertString(doc.length, "  ${check.failMessage}\n", textStyles.red)
        doc.insertString(doc.length, "  Current: ${result.displayValue}\n\n", textStyles.black)
      }
    }
  }
}
