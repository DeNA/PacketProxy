/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.gui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

public class FilterComboBoxCellRenderer implements ListCellRenderer<String>
{
	protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

	@Override
	public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus)
	{
		JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		if (isSelected || cellHasFocus) {
			renderer.setForeground(new Color(0xff, 0xff, 0xff));
			renderer.setBackground(new Color(0x80, 0x80, 0xff));
		} else {
			renderer.setForeground(new Color(0x00, 0x00, 0x00));
			renderer.setBackground(new Color(0xff, 0xff, 0xff));
		}
		return renderer;
	}
}
