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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import packetproxy.model.InterceptOption;
import packetproxy.model.InterceptOptions;

public class GUIOptionIntercepts extends GUIOptionComponentBase<InterceptOption>
{
	private GUIOptionInterceptDialog dlg;
	InterceptOptions intercept_options;
	List<InterceptOption> table_ext_list;

	public GUIOptionIntercepts(JFrame owner) throws Exception {
		super(owner);
		intercept_options = InterceptOptions.getInstance();
		intercept_options.addObserver(this);
		table_ext_list = new ArrayList<InterceptOption>();

		String[] menu = { "Enabled", "Direction", "Match Type", "Relationship", "Method", "Pattern", "Applied Server" };
		int[] menuWidth = { 50, 100, 100, 100, 50, 180, 150 };
		MouseAdapter tableAction = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					int columnIndex= table.columnAtPoint(e.getPoint());
					int rowIndex= table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */
						boolean enable_checkbox = (Boolean)table.getValueAt(rowIndex, 0);
						InterceptOption intercept = getSelectedTableContent();
						if (enable_checkbox == true) {
							intercept.setDisabled();
						} else {
							intercept.setEnabled();
						}
						intercept_options.update(intercept);
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
					dlg = new GUIOptionInterceptDialog(owner);
					InterceptOption intercept = dlg.showDialog();
					if (intercept != null) {
						intercept_options.create(intercept);
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
					InterceptOption old_intercept = getSelectedTableContent();
					dlg = new GUIOptionInterceptDialog(owner);
					InterceptOption new_intercept = dlg.showDialog(old_intercept);
					if (new_intercept != null) {
						intercept_options.delete(old_intercept);
						intercept_options.create(new_intercept);
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
					intercept_options.delete(getSelectedTableContent());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);

		updateImpl();
	}

	@Override
	protected void addTableContent(InterceptOption intercept) {
		table_ext_list.add(intercept);
		try {
			option_model.addRow(new Object[] {
				intercept.isEnabled(),
					intercept.getDirection(),
					intercept.getType(),
					intercept.getRelationship(),
					intercept.getMethod(),
					intercept.getPattern(),
					intercept.getServerName()
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void updateTable(List<InterceptOption> interceptList) {
		clearTableContents();
		for (InterceptOption intercept : interceptList) {
			addTableContent(intercept);
		}
	}

	@Override
	protected void updateImpl() {
		try {
			updateTable(intercept_options.queryAll());
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
	protected InterceptOption getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected InterceptOption getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}
}
