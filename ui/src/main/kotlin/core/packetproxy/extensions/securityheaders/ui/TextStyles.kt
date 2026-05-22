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
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Text styles for security headers display. Provides predefined color and style attributes for
 * highlighting security check results.
 */
class TextStyles {
  val green: SimpleAttributeSet
  val red: SimpleAttributeSet
  val yellow: SimpleAttributeSet
  val black: SimpleAttributeSet
  val bold: SimpleAttributeSet

  init {
    green = SimpleAttributeSet()
    StyleConstants.setForeground(green, Color(0, 128, 0))
    StyleConstants.setBackground(green, Color(240, 255, 240))

    red = SimpleAttributeSet()
    StyleConstants.setForeground(red, Color(200, 0, 0))
    StyleConstants.setBold(red, true)
    StyleConstants.setBackground(red, Color(255, 240, 240))

    yellow = SimpleAttributeSet()
    StyleConstants.setForeground(yellow, Color(220, 130, 0))
    StyleConstants.setBackground(yellow, Color(255, 255, 240))

    black = SimpleAttributeSet()
    StyleConstants.setForeground(black, Color.BLACK)

    bold = SimpleAttributeSet()
    StyleConstants.setBold(bold, true)
    StyleConstants.setForeground(bold, Color.BLACK)
  }
}
