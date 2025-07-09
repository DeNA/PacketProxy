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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import packetproxy.model.ListenPort;
import packetproxy.model.ListenPort.TYPE;
import packetproxy.model.ListenPorts;

public class GUIOptionListenPorts extends GUIOptionComponentBase<ListenPort> {
	private GUIOptionListenPortDialog dlg;
	private ListenPorts listenPorts;
	private List<ListenPort> table_ext_list;

	public GUIOptionListenPorts(JFrame owner) throws Exception {
		super(owner);
		listenPorts = ListenPorts.getInstance();
		listenPorts.addPropertyChangeListener(this);
		table_ext_list = new ArrayList<ListenPort>();

		String[] menu = {"Enabled", "Protocol", "Listen Port", "Port Type", "CA", "Forward Server"};
		int[] menuWidth = {50, 50, 80, 120, 250, 300};
		MouseAdapter tableAction = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */
						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 0);
						ListenPort lp = getSelectedTableContent();
						if (enable_checkbox == true) {
							lp.setDisabled();
						} else {
							lp.setEnabled();
						}
						listenPorts.update(lp);
					}
					table.setRowSelectionInterval(rowIndex, rowIndex);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		ActionListener addAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					dlg = new GUIOptionListenPortDialog(owner);
					ListenPort listenPort = dlg.showDialog();
					if (listenPort != null) {
						listenPort.setEnabled();
						listenPorts.create(listenPort);
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		ActionListener editAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					ListenPort old_port = getSelectedTableContent();
					dlg = new GUIOptionListenPortDialog(owner);
					ListenPort new_port = dlg.showDialog(old_port);
					if (new_port != null) {
						listenPorts.delete(old_port);
						new_port.setEnabled();
						listenPorts.create(new_port);
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		ActionListener removeAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					listenPorts.delete(getSelectedTableContent());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(ListenPort listenPort) {
		table_ext_list.add(listenPort);
		try {
			String serverNullStr = "";
			TYPE type = listenPort.getType();
			if (type == TYPE.FORWARDER || type == TYPE.SSL_FORWARDER || type == TYPE.UDP_FORWARDER
					|| type == TYPE.QUIC_FORWARDER)
				serverNullStr = "Deleted";
			option_model.addRow(new Object[]{listenPort.isEnabled(), listenPort.getProtocol(), listenPort.getPort(),
					listenPort.getType(), listenPort.getCA().map(ca -> ca.getName()).orElse("Error"),
					listenPort.getServer() != null ? listenPort.getServer().toString() : serverNullStr});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void updateTable(List<ListenPort> listenList) {
		clearTableContents();
		for (ListenPort listenPort : listenList) {
			addTableContent(listenPort);
		}
	}

	@Override
	protected void updateImpl() {
		try {
			updateTable(listenPorts.queryAll());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void clearTableContents() {
		option_model.setRowCount(0);
		table_ext_list.clear();
	}

	@Override
	protected ListenPort getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected ListenPort getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}
}
