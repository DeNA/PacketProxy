/*
 * Copyright 2021 DeNA Co., Ltd.
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

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableCellRenderer;
import packetproxy.model.OneShotPacket;
import packetproxy.model.OptionTableModel;

public class GUIVulCheckSendTable {

	private String[] columnNames;
	private int[] columnWidth = {20, 200, 100, 20, 20, 20};
	private OptionTableModel tableModel;
	private JTable table;
	private Consumer<String> onSelected;
	private Function<String, Boolean> onEnabled;
	private Function<String, Boolean> onDisabled;

	public GUIVulCheckSendTable(Consumer<String> onSelected, Function<String, Boolean> onEnabled,
			Function<String, Boolean> onDisabled) {
		this.onSelected = onSelected;
		this.onEnabled = onEnabled;
		this.onDisabled = onDisabled;
	}

	public JComponent createPanel() throws Exception {
		columnNames = new String[]{"Enabled", "Name", "Client Request", "Length", "Encode", "ALPN"};
		tableModel = new OptionTableModel(columnNames, 0) {

			private static final long serialVersionUID = 1L;
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

					boolean selected = (table.getSelectedRow() == row);
					if (selected) {

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

					e.printStackTrace();
				}
				return c;
			}
		};
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);
		table.setAutoCreateRowSorter(true);

		for (int i = 0; i < columnNames.length; i++) {

			table.getColumn(columnNames[i]).setPreferredWidth(columnWidth[i]);
		}

		table.addKeyListener(new KeyAdapter() {

			public void keyPressed(KeyEvent e) {
				try {

					if (e.getKeyCode() == KeyEvent.VK_J) {

						int p = table.getSelectedRow() + 1;
						p = p >= table.getRowCount() ? table.getRowCount() - 1 : p;
						table.changeSelection(p, 0, false, false);
					} else if (e.getKeyCode() == KeyEvent.VK_K) {

						int p = table.getSelectedRow() - 1;
						p = p < 0 ? 0 : p;
						table.changeSelection(p, 0, false, false);
					}
				} catch (Exception e1) {

					// Nothing to do
				}
			}
		});

		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					String generatorName = getSelectedGeneratorName();
					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 0);
						if (enable_checkbox) {

							if (onDisabled.apply(generatorName)) {

								table.setValueAt(false, rowIndex, 0);
							}
						} else {

							if (onEnabled.apply(generatorName)) {

								table.setValueAt(true, rowIndex, 0);
							}
						}
					}
					table.setRowSelectionInterval(rowIndex, rowIndex);
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		table.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				try {

					String generatorName = getSelectedGeneratorName();
					onSelected.accept(generatorName);
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		return new JScrollPane(table);
	}

	public String getSelectedGeneratorName() {
		int idx = table.getSelectedRow();
		if (0 <= idx && idx < table.getRowCount())
			return (String) table.getValueAt(idx, 1);
		else
			return "";
	}

	public void add(String name, OneShotPacket oneshot, boolean enabled) throws Exception {
		tableModel.addRow(makeRowDataFromPacket(name, oneshot, enabled));
	}

	public void setRow(String generatorName, OneShotPacket oneshot) throws Exception {
		for (int i = 0; i < table.getRowCount(); i++) {

			String name = (String) table.getValueAt(i, 1);
			if (name.equals(generatorName)) {

				table.setValueAt(oneshot.getSummarizedRequest(), i, 2);
				table.setValueAt(oneshot.getData().length, i, 3);
				table.setValueAt(oneshot.getEncoder(), i, 4);
				table.setValueAt(oneshot.getAlpn(), i, 5);
			}
		}
	}

	public void clear() {
		tableModel.setRowCount(0);
	}

	private Object[] makeRowDataFromPacket(String name, OneShotPacket oneshot, boolean enabled) throws Exception {
		return new Object[]{enabled, name, oneshot.getSummarizedRequest(), oneshot.getData().length,
				oneshot.getEncoder(), oneshot.getAlpn()};
	}
}
