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

import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableCellRenderer;
import packetproxy.common.Utils;
import packetproxy.model.OneShotPacket;
import packetproxy.model.OptionTableModel;
import packetproxy.model.RegexParam;

public class GUIBulkSenderTable {

	private String[] columnNames;
	private int[] columnWidth = {60, 800};
	private OptionTableModel tableModel;
	private JTable table;
	boolean updating = false;
	private Type type;
	private Consumer<Integer> onSelected;
	private List<RegexParam> regexParams;

	public enum Type {
		CLIENT, SERVER
	};

	public GUIBulkSenderTable(Type type, Consumer<Integer> onSelected) {
		this.type = type;
		this.onSelected = onSelected;
		this.regexParams = new ArrayList<>();
	}

	private JMenuItem createMenuItem(String name, int key, KeyStroke hotkey, ActionListener l) {
		JMenuItem out = new JMenuItem(name);
		if (key >= 0) {

			out.setMnemonic(key);
		}
		if (hotkey != null) {

			out.setAccelerator(hotkey);
		}
		out.addActionListener(l);
		return out;
	}

	public JComponent createPanel() throws Exception {

		if (type == Type.CLIENT)
			columnNames = new String[]{"#", "Client Request"};
		else if (type == Type.SERVER)
			columnNames = new String[]{"#", "Server Response"};

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

					errWithStackTrace(e);
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

		if (this.type == Type.CLIENT) {

			JMenuItem paramsMenu = createMenuItem("use params", -1, null, new ActionListener() {

				public void actionPerformed(ActionEvent actionEvent) {
					try {

						log("TODO");
						JFrame owner = GUIMain.getInstance();
						int packetId = getSelectedPacketId();
						GUIRegexParamsTableDialog dlg = new GUIRegexParamsTableDialog(owner, regexParams, packetId);
						regexParams = dlg.showDialog();
					} catch (Exception e) {

						errWithStackTrace(e);
					}
				}
			});

			JPopupMenu menu = new JPopupMenu();
			menu.add(paramsMenu);

			table.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseReleased(MouseEvent event) {
					if (Utils.isWindows() && event.isPopupTrigger()) {

						menu.show(event.getComponent(), event.getX(), event.getY());
					}
				}

				@Override
				public void mousePressed(MouseEvent event) {
					try {

						if (event.isPopupTrigger()) {

							menu.show(event.getComponent(), event.getX(), event.getY());
						}
					} catch (Exception e) {

						errWithStackTrace(e);
					}
				}
			});
		}

		table.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				try {

					int select_id = getSelectedPacketId();
					onSelected.accept(select_id);
				} catch (Exception e1) {

					errWithStackTrace(e1);
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

	public void add(OneShotPacket oneshot) throws Exception {
		tableModel.addRow(makeRowDataFromPacket(oneshot));
	}

	public void clear() {
		tableModel.setRowCount(0);
		regexParams.clear();
	}

	private Object[] makeRowDataFromPacket(OneShotPacket oneshot) throws Exception {
		if (this.type == Type.CLIENT) {

			return new Object[]{oneshot.getId(), oneshot.getSummarizedRequest()};
		} else {

			return new Object[]{oneshot.getId(), oneshot.getSummarizedResponse()};
		}
	}

	public List<RegexParam> getRegexParams() {
		return this.regexParams;
	}
}
