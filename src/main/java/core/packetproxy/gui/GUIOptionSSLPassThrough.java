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
import static packetproxy.model.PropertyChangeEventType.SSL_PASS_THROUGHS;
import static packetproxy.util.Logging.errWithStackTrace;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import packetproxy.model.SSLPassThrough;
import packetproxy.model.SSLPassThroughs;

public class GUIOptionSSLPassThrough extends GUIOptionComponentBase<SSLPassThrough> {

	private GUIOptionSSLPassThroughDialog dlg;
	SSLPassThroughs sslPassThroughs;
	List<SSLPassThrough> table_ext_list;

	public GUIOptionSSLPassThrough(JFrame owner) throws Exception {
		super(owner);
		this.sslPassThroughs = SSLPassThroughs.getInstance();
		this.sslPassThroughs.addPropertyChangeListener(this);
		this.table_ext_list = new ArrayList<SSLPassThrough>();

		String[] menu = {"Enabled", "Server Name", "Applied Listen Port"};
		int[] menuWidth = {80, 570, 150};
		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 0);
						SSLPassThrough ssl = getSelectedTableContent();
						if (enable_checkbox == true) {

							ssl.setDisabled();
						} else {

							ssl.setEnabled();
						}
						sslPassThroughs.update(ssl);
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

					dlg = new GUIOptionSSLPassThroughDialog(owner);
					SSLPassThrough ssl = dlg.showDialog();
					if (ssl != null) {

						ssl.setEnabled();
						SSLPassThroughs.getInstance().create(ssl);
					}
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener editAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					SSLPassThrough old_ssl = getSelectedTableContent();
					dlg = new GUIOptionSSLPassThroughDialog(owner);
					SSLPassThrough new_ssl = dlg.showDialog(old_ssl);
					if (new_ssl != null) {

						SSLPassThroughs.getInstance().delete(old_ssl);
						new_ssl.setEnabled();
						SSLPassThroughs.getInstance().create(new_ssl);
					}
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener removeAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					SSLPassThroughs.getInstance().delete(getSelectedTableContent());
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(SSLPassThrough ssl) {
		table_ext_list.add(ssl);
		try {

			option_model.addRow(new Object[]{ssl.isEnabled(), ssl.getServerName(),
					(ssl.getListenPort() == SSLPassThrough.ALL_PORTS) ? "*" : ssl.getListenPort()});
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	protected void updateTable(List<SSLPassThrough> sslList) {
		clearTableContents();
		for (SSLPassThrough ssl : sslList) {

			addTableContent(ssl);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(sslPassThroughs.queryAll());
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	protected void clearTableContents() {
		option_model.setRowCount(0);
		table_ext_list.clear();
	}

	@Override
	protected SSLPassThrough getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected SSLPassThrough getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (SSL_PASS_THROUGHS.matches(evt)) {

			updateImpl();
		}
	}
}
