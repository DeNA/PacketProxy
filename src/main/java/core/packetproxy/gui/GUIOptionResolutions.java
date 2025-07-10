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
import packetproxy.model.Resolution;
import packetproxy.model.Resolutions;

public class GUIOptionResolutions extends GUIOptionComponentBase<Resolution> {

	private GUIOptionResolutionDialog dlg;
	Resolutions resolutions;
	List<Resolution> table_ext_list;

	public GUIOptionResolutions(JFrame owner) throws Exception {
		super(owner);
		this.resolutions = Resolutions.getInstance();
		this.resolutions.addPropertyChangeListener(this);
		this.table_ext_list = new ArrayList<Resolution>();
		String[] menu = {"IP Addr", "Host", "Override", "Comment"};
		int[] menuWidth = {200, 200, 50, 100};

		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 2) { /* Override area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 2);
						Resolution resolution = getSelectedTableContent();
						if (enable_checkbox == true) {

							resolution.disableResolution();
						} else {

							resolution.enableResolution();
						}
						resolutions.update(resolution);
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

					dlg = new GUIOptionResolutionDialog(owner);
					Resolution resolution = dlg.showDialog();
					resolutions.create(resolution);
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		};
		ActionListener editAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					Resolution old_resolution = getSelectedTableContent();
					dlg = new GUIOptionResolutionDialog(owner);
					Resolution new_resolution = dlg.showDialog(old_resolution);
					if (new_resolution != null) {

						resolutions.delete(old_resolution);
						resolutions.create(new_resolution);
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

					resolutions.delete(getSelectedTableContent());
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(Resolution resolution) {
		table_ext_list.add(resolution);
		try {

			option_model.addRow(new Object[]{resolution.getIp(), resolution.getHostName(), resolution.isEnabled(),
					resolution.getComment()});
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	@Override
	protected void updateTable(List<Resolution> resolutionList) {
		clearTableContents();
		for (Resolution resolution : resolutionList) {

			addTableContent(resolution);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(resolutions.queryAll());
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
	protected Resolution getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected Resolution getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}
}
