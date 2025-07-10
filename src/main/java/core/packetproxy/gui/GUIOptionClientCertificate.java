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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import packetproxy.model.ClientCertificate;
import packetproxy.model.ClientCertificates;

public class GUIOptionClientCertificate extends GUIOptionComponentBase<ClientCertificate> {

	private GUIOptionClientCertificateDialog dlg;
	private ClientCertificates clientCertificates;
	private List<ClientCertificate> table_ext_list;

	public GUIOptionClientCertificate(JFrame owner) throws Exception {
		super(owner);
		clientCertificates = ClientCertificates.getInstance();
		clientCertificates.addPropertyChangeListener(this);
		table_ext_list = new ArrayList<>();

		String[] menu = {"Enabled", "Type", "Host", "Subject(CN)", "Issuer"};
		int[] menuWidth = {50, 50, 200, 100, 350};

		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 0);
						ClientCertificate certificate = getSelectedTableContent();
						if (enable_checkbox) {

							certificate.setDisabled();
						} else {

							certificate.setEnabled();
						}
						clientCertificates.update(certificate);
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

					dlg = new GUIOptionClientCertificateDialog(owner);
					ClientCertificate certificate = dlg.showDialog();
					if (certificate != null) {

						clientCertificates.create(certificate);
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

					ClientCertificate oldClientCertificate = getSelectedTableContent();
					dlg = new GUIOptionClientCertificateDialog(owner);
					ClientCertificate certificate = dlg.showDialog(oldClientCertificate);
					if (certificate != null) {

						clientCertificates.delete(oldClientCertificate);
						clientCertificates.create(certificate);
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

					clientCertificates.delete(getSelectedTableContent());
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(ClientCertificate certificate) {
		table_ext_list.add(certificate);
		try {

			option_model.addRow(new Object[]{certificate.isEnabled(), certificate.getType().getText(),
					certificate.getServerName(), certificate.getSubject(), certificate.getIssuer(),});
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	@Override
	protected void updateTable(List<ClientCertificate> certificateList) {
		clearTableContents();
		for (ClientCertificate certificate : certificateList) {

			addTableContent(certificate);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(clientCertificates.queryAll());
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
	protected ClientCertificate getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected ClientCertificate getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}
}
