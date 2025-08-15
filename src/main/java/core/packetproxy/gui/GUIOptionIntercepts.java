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
import javax.swing.JOptionPane;
import packetproxy.common.I18nString;
import packetproxy.model.InterceptOption;
import packetproxy.model.InterceptOption.Direction;
import packetproxy.model.InterceptOptions;

public class GUIOptionIntercepts extends GUIOptionComponentBase<InterceptOption> {

	InterceptOptions intercept_options;
	List<InterceptOption> table_ext_list;

	public GUIOptionIntercepts(JFrame owner) throws Exception {
		super(owner);
		intercept_options = InterceptOptions.getInstance();
		intercept_options.addPropertyChangeListener(this);
		table_ext_list = new ArrayList<InterceptOption>();

		String[] menu = {"Enabled", "Direction", "Action and Condition", "Type", "Pattern", "Target Server"};
		int[] menuWidth = {50, 160, 300, 50, 80, 90};
		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* check box area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 0);
						InterceptOption intercept = getSelectedTableContent();
						if (enable_checkbox == true) {

							if (intercept.isDirection(Direction.ALL_THE_OTHER_REQUESTS)
									|| intercept.isDirection(Direction.ALL_THE_OTHER_RESPONSES)) {

								JOptionPane.showMessageDialog(owner, I18nString.get("This entry can't be disabled."));
							} else {

								intercept.setDisabled();
							}
						} else {

							intercept.setEnabled();
						}
						intercept_options.update(intercept);
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

					GUIOptionInterceptDialog dlg = new GUIOptionInterceptDialog(owner);
					InterceptOption intercept = dlg.showDialog();
					if (intercept != null) {

						intercept_options.create(intercept);
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

					InterceptOption old_intercept = getSelectedTableContent();
					InterceptOption new_intercept = null;
					if (old_intercept.isDirection(Direction.ALL_THE_OTHER_REQUESTS)
							|| old_intercept.isDirection(Direction.ALL_THE_OTHER_RESPONSES)) {

						new_intercept = new GUIOptionInterceptEditOthersDialog(owner).showDialog(old_intercept);
					} else {

						new_intercept = new GUIOptionInterceptDialog(owner).showDialog(old_intercept);
					}
					if (new_intercept != null) {

						new_intercept.setId(old_intercept.getId());
						intercept_options.update(new_intercept);
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

					InterceptOption intercept = getSelectedTableContent();
					if (intercept.isDirection(Direction.ALL_THE_OTHER_REQUESTS)
							|| intercept.isDirection(Direction.ALL_THE_OTHER_RESPONSES)) {

						JOptionPane.showMessageDialog(owner, I18nString.get("This entry can't be removed."));
					} else {

						intercept_options.delete(intercept);
					}
				} catch (Exception e1) {

					errWithStackTrace(e1);
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

			option_model.addRow(new Object[]{intercept.isEnabled(), intercept.getDirectionAsString(),
					intercept.getRelationshipAsString(), intercept.getMethodAsString(), intercept.getPattern(),
					intercept.getServerName()});
		} catch (Exception e) {

			errWithStackTrace(e);
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

			errWithStackTrace(e);
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
