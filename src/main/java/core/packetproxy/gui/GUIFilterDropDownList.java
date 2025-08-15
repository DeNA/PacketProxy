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
import static packetproxy.util.Logging.err;
import static packetproxy.util.Logging.errWithStackTrace;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import packetproxy.common.I18nString;
import packetproxy.model.Filter;
import packetproxy.model.Filters;

public class GUIFilterDropDownList extends JDialog {

	private static final long serialVersionUID = 1L;
	private List<Filter> defaultFilters;
	private JTable table;

	public int showDialog() {
		setVisible(true);
		return table.getHeight();
	}

	private int[] mouseRow;

	public GUIFilterDropDownList(JFrame owner, int width, Consumer<Filter> consumer) throws Exception {
		super(owner);
		this.setUndecorated(true);
		Container c = getContentPane();
		c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));

		mouseRow = new int[1];
		mouseRow[0] = -1;

		// デフォルトのフィルタルール
		defaultFilters = new ArrayList<Filter>();
		Filter defaultFilter = new Filter(I18nString.get("No image,css,js,font"),
				"type != image && type != css && type != javascript && type != font");
		defaultFilters.add(defaultFilter);

		String[] columnNames = {"filter name", "filter"};
		int[] columnWidth = {150, width - 150};
		DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		table = new JTable(tableModel) {

			private static final long serialVersionUID = 1L;

			@Override
			public Component prepareRenderer(TableCellRenderer tcr, int row, int column) {
				Component c = super.prepareRenderer(tcr, row, column);
				try {

					if (row == mouseRow[0]) {

						c.setForeground(new Color(0xff, 0xff, 0xff));
						c.setBackground(new Color(0x80, 0x80, 0xff));
					} else {

						c.setForeground(new Color(0x00, 0x00, 0x00));
						if (row % 2 == 0)
							c.setBackground(new Color(0xff, 0xff, 0xff));
						else
							c.setBackground(new Color(0xf0, 0xf0, 0xf0));
					}
				} catch (Exception e) {

					errWithStackTrace(e);
				}
				return c;
			}
		};

		table.addMouseMotionListener(new MouseMotionAdapter() {

			@Override
			public void mouseMoved(MouseEvent e) {
				Point p = e.getPoint();
				mouseRow[0] = table.rowAtPoint(p);
				table.repaint();
			}
		});

		table.setTableHeader(null);
		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent arg0) {
				try {

					int idx = table.getSelectedRow();
					if (0 <= idx && idx < table.getRowCount()) {

						String name = (String) table.getValueAt(idx, 0);
						String filter = (String) table.getValueAt(idx, 1);
						consumer.accept(new Filter(name, filter));
					} else {

						err(Integer.toString(idx));
					}
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});
		if (Filters.getInstance().queryAll().isEmpty() && defaultFilters.isEmpty()) {

			tableModel.addRow(new String[]{"--", I18nString.get("No filter")});
		} else {

			Filters.getInstance().queryAll().stream()
					.forEach(filter -> tableModel.addRow(new String[]{filter.getName(), filter.getFilter()}));
			defaultFilters.forEach(filter -> tableModel.addRow(new String[]{filter.getName(), filter.getFilter()}));
		}
		for (int i = 0; i < columnNames.length; i++) {

			table.getColumn(columnNames[i]).setPreferredWidth(columnWidth[i]);
		}
		LineBorder border = new LineBorder(Color.GRAY, 1, false);
		table.setBorder(border);
		c.add(table);
	}
}
