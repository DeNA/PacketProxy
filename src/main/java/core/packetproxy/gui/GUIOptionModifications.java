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

import static packetproxy.util.Logging.log;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import packetproxy.model.Modification;
import packetproxy.model.Modifications;

public class GUIOptionModifications extends GUIOptionComponentBase<Modification> {

	private GUIOptionModificationDialog dlg;
	private Modifications modifications;
	private List<Modification> table_ext_list;

	public GUIOptionModifications(JFrame owner) throws Exception {
		super(owner);
		modifications = Modifications.getInstance();
		modifications.addPropertyChangeListener(this);
		table_ext_list = new ArrayList<Modification>();
		String[] menu = {"Enabled", "Type", "Method", "Pattern", "Replaced", "Applied Server"};
		int[] menuWidth = {50, 100, 50, 180, 180, 150};
		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 0);
						Modification mod = getSelectedTableContent();
						if (enable_checkbox == true) {

							mod.setDisabled();
						} else {

							mod.setEnabled();
						}
						modifications.update(mod);
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

					dlg = new GUIOptionModificationDialog(owner);
					Modification mod = dlg.showDialog();
					if (mod != null) {

						mod.setEnabled();
						modifications.create(mod);
					}
					log("Modification button is pressed.");
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		};
		ActionListener editAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					Modification old_mod = getSelectedTableContent();
					dlg = new GUIOptionModificationDialog(owner);
					Modification new_mod = dlg.showDialog(old_mod);
					if (new_mod != null) {

						modifications.delete(old_mod);
						new_mod.setEnabled();
						modifications.create(new_mod);
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

					modifications.delete(getSelectedTableContent());
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(Modification mod) {
		table_ext_list.add(mod);
		try {

			option_model.addRow(new Object[]{mod.isEnabled(), mod.getDirection(), mod.getMethod(), mod.getPattern(),
					mod.getReplaced(), mod.getServerName()});
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	@Override
	protected void updateTable(List<Modification> modList) {
		clearTableContents();
		for (Modification mod : modList) {

			addTableContent(mod);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(modifications.queryAll());
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
	protected Modification getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected Modification getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}
}
