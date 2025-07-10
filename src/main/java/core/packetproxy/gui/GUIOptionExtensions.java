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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import packetproxy.model.Extension;
import packetproxy.model.Extensions;

public class GUIOptionExtensions extends GUIOptionComponentBase<Extension> implements PropertyChangeListener {

	Extensions extensions;
	List<Extension> table_ext_list;

	public GUIOptionExtensions(JFrame owner) throws Exception {
		super(owner);
		extensions = Extensions.getInstance();
		extensions.addPropertyChangeListener(this);
		table_ext_list = new ArrayList<Extension>();

		String[] menu = {"Enabled", "Name", "Path"};
		int[] menuWidth = {30, 300, 300};
		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 0);
						Extension ext = getSelectedTableContent();
						ext.setEnabled(!enable_checkbox);
						ext = extensions.update(ext);
						if (!enable_checkbox && ext != null) {

							GUIExtensions.getInstance().addExtension(ext);
						} else if (enable_checkbox) {

							GUIExtensions.getInstance().removeExtension(ext);
						}
					}
					table.setRowSelectionInterval(rowIndex, columnIndex);
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		};
		ActionListener addAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIOptionExtensionsDialog dlg = new GUIOptionExtensionsDialog(owner);
					Extension ext = dlg.showDialog();
					if (ext != null) {

						Extensions.getInstance().create(ext);
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

					Extension old_ext = getSelectedTableContent();
					GUIOptionExtensionsDialog dlg = new GUIOptionExtensionsDialog(owner);
					Extension ext = dlg.showDialog(old_ext);
					if (ext != null && (ext.getName() != old_ext.getName() || ext.getPath() != old_ext.getPath())) {

						GUIExtensions.getInstance().removeExtension(old_ext);
						Extensions.getInstance().delete(old_ext);
						Extensions.getInstance().create(ext);
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

					GUIExtensions.getInstance().removeExtension(getSelectedTableContent());
					Extensions.getInstance().delete(getSelectedTableContent());
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(Extension ext) {
		table_ext_list.add(ext);
		try {

			option_model.addRow(new Object[]{ext.isEnabled(), ext.getName(), ext.getPath(),});
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	@Override
	protected void updateTable(List<Extension> exts) {
		clearTableContents();
		for (Extension ext : exts) {

			addTableContent(ext);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(extensions.queryAll());
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
	protected Extension getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected Extension getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		updateImpl();
	}
}
