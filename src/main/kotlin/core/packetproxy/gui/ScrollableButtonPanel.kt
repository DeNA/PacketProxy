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

import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable

/**
 * ビューポートが十分に広い場合はボタンを中央寄せし、 狭い場合は横スクロールバーを表示するためのパネル。 getScrollableTracksViewportWidth()
 * でビューポート幅に追従するかを切り替える。
 */
class ScrollableCenteredPanel : JPanel(FlowLayout(FlowLayout.CENTER)), Scrollable {
  override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

  override fun getScrollableUnitIncrement(
    visibleRect: Rectangle,
    orientation: Int,
    direction: Int,
  ): Int = 20

  override fun getScrollableBlockIncrement(
    visibleRect: Rectangle,
    orientation: Int,
    direction: Int,
  ): Int = 100

  override fun getScrollableTracksViewportWidth(): Boolean =
    parent != null && parent.width >= preferredSize.width

  override fun getScrollableTracksViewportHeight(): Boolean = true
}

object ScrollableButtonPanel {
  fun createScrollPane(buttonPanel: JPanel): JScrollPane {
    val scrollPane =
      object :
        JScrollPane(
          buttonPanel,
          ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
        ) {
        override fun getPreferredSize(): Dimension {
          val dimension = super.getPreferredSize()
          val horizontalBar = horizontalScrollBar
          // スクロールバーが表示されている場合のみ高さを加算することで、
          // 非表示時の余分なスペースを排除しつつ、表示時はレイアウトを押し下げて領域を確保する
          if (horizontalBar != null && horizontalBar.isVisible) {
            dimension.height += horizontalBar.preferredSize.height
          }
          return dimension
        }
      }
    scrollPane.border = null
    scrollPane.viewport.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          scrollPane.revalidate()
          scrollPane.repaint()
        }
      }
    )
    return scrollPane
  }
}
