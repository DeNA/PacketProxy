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
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableCellRenderer;
import packetproxy.model.OneShotPacket;
import packetproxy.model.OptionTableModel;

public class GUIVulCheckRecvTable {
	private String[] columnNames;
	private int[] columnWidth = {30, 200, 40, 40, 50, 50, 50};
	private OptionTableModel tableModel;
	private JTable table;
	private Consumer<Integer> onSelected;

	public GUIVulCheckRecvTable(Consumer<Integer> onSelected) {
		this.onSelected = onSelected;
	}

	public JComponent createPanel() throws Exception {
		columnNames = new String[]{"#", "Name", "Server Response", "Length", "Time[msec]", "Encode", "ALPN"};
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

		table.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				try {
					int select_id = getSelectedPacketId();
					onSelected.accept(select_id);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		return new JScrollPane(table);
	}

	public int getSelectedPacketId() {
		int idx = table.getSelectedRow();
		if (0 <= idx && idx < table.getRowCount())
			return (Integer) table.getValueAt(idx, 0);
		else
			return 0;
	}

	public void add(int id, String name, OneShotPacket oneshot, long rtt) throws Exception {
		tableModel.addRow(makeRowDataFromPacket(id, name, oneshot, rtt));
	}

	public void clear() {
		tableModel.setRowCount(0);
	}

	private Object[] makeRowDataFromPacket(int id, String name, OneShotPacket oneshot, long rtt) throws Exception {
		return new Object[]{id, name, oneshot.getSummarizedResponse(), oneshot.getData().length, rtt,
				oneshot.getEncoder(), oneshot.getAlpn()};
	}
}
