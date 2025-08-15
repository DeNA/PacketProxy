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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class GUIOptionServers extends GUIOptionComponentBase<Server> {

	private GUIOptionServerDialog dlg;
	private Servers servers;
	private List<Server> server_list;

	public GUIOptionServers(JFrame owner) throws Exception {
		super(owner);
		servers = Servers.getInstance();
		servers.addPropertyChangeListener(this);
		server_list = new ArrayList<Server>();
		String[] menu = {"Host", "Port", "Use SSL", "Encode Module", "Spoof DNS(A)", "Spoof DNS(AAAA)", "HttpProxy",
				"Comment"};
		int[] menuWidth = {200, 80, 50, 160, 60, 60, 60, 100};
		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 4) { /* Spoof DNS area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 4);
						Server server = getSelectedTableContent();
						if (enable_checkbox == true) {

							server.disableResolved();
						} else {

							server.enableResolved();
						}
						servers.update(server);
					}
					if (columnIndex == 5) { /* Spoof DNS area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 5);
						Server server = getSelectedTableContent();
						if (enable_checkbox == true) {

							server.disableResolved6();
						} else {

							server.enableResolved6();
						}
						servers.update(server);
					}
					table.setRowSelectionInterval(rowIndex, rowIndex);
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener addAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					dlg = new GUIOptionServerDialog(owner);
					Server server = dlg.showDialog();
					servers.create(server);
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener editAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					Server old_server = getSelectedTableContent();
					dlg = new GUIOptionServerDialog(owner);
					Server new_server = dlg.showDialog(old_server);
					Servers.getInstance().update(new_server);
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener removeAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					Server server = getSelectedTableContent();
					servers.delete(server);
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		jcomponent = createComponentForServers(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(Server server) {
		server_list.add(server);
		option_model.addRow(new Object[]{server.getIp(), server.getPort(), server.getUseSSL(), server.getEncoder(),
				server.isResolved(), server.isResolved6(), server.isHttpProxy(), server.getComment()});
	}

	@Override
	protected void updateTable(List<Server> serverList) {
		clearTableContents();
		for (Server server : serverList) {

			addTableContent(server);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(servers.queryAll());
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	protected void clearTableContents() {
		server_list.clear();
		option_model.setRowCount(0);
	}

	@Override
	protected Server getSelectedTableContent() {
		int index = table.getSelectedRow();
		return getTableContent(table.getRowSorter().convertRowIndexToModel(index));
	}

	@Override
	protected Server getTableContent(int rowIndex) {
		return server_list.get(rowIndex);
	}
}
