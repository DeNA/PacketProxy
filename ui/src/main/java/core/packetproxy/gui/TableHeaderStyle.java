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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Utility class for applying consistent header styles to JTable. Preserves sort
 * icons on the right edge while maintaining left-aligned text.
 */
public class TableHeaderStyle {

	/**
	 * Applies a consistent header style to all columns of a JTable. This method
	 * preserves sort icons and positions them on the right edge.
	 *
	 * @param table
	 *            the JTable to style
	 * @param columnCount
	 *            the number of columns to apply styling to
	 */
	public static void apply(JTable table, int columnCount) {
		TableCellRenderer defaultHeaderRenderer = table.getTableHeader().getDefaultRenderer();
		TableCellRenderer headerRenderer = new TableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = defaultHeaderRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus,
						row, column);
				if (c instanceof JLabel) {
					JLabel originalLabel = (JLabel) c;

					// Create a panel with BorderLayout to position text on left and icon on right
					// edge
					JPanel panel = new JPanel(new BorderLayout());
					panel.setOpaque(true);
					panel.setBackground(originalLabel.getBackground());

					// Create text label on the left
					JLabel textLabel = new JLabel(originalLabel.getText());
					textLabel.setFont(originalLabel.getFont());
					textLabel.setForeground(originalLabel.getForeground());

					// Create icon label on the right edge (for sort icon)
					JLabel iconLabel = new JLabel(originalLabel.getIcon());

					panel.add(textLabel, BorderLayout.WEST);
					panel.add(iconLabel, BorderLayout.EAST);

					panel.setBorder(BorderFactory.createCompoundBorder(
							BorderFactory.createMatteBorder(0, 0, 1, 1, Color.LIGHT_GRAY),
							BorderFactory.createEmptyBorder(2, 5, 2, 5) // top, left, bottom, right padding
					));

					return panel;
				}
				return c;
			}
		};
		for (int i = 0; i < columnCount; i++) {
			table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
		}
	}
}
