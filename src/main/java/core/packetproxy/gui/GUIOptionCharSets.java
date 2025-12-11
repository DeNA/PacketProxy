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
import javax.swing.*;
import packetproxy.model.CharSet;
import packetproxy.model.CharSets;

public class GUIOptionCharSets extends GUIOptionComponentBase<CharSet> {

	private GUIOptionCharSetDialog dlg;
	private CharSets charsets;
	private List<CharSet> charsets_list;

	public GUIOptionCharSets(JFrame owner) throws Exception {
		super(owner);
		charsets = CharSets.getInstance();
		charsets.addPropertyChangeListener(this);
		charsets_list = new ArrayList<CharSet>();
		String[] menu = {"CharSetName"};
		int[] menuWidth = {200, 80, 50, 160, 60, 60, 100};
		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					if (columnIndex == 4) {
						/* Spoof DNS area */

						boolean enable_checkbox = (Boolean) table.getValueAt(rowIndex, 4);
						CharSet charset = getSelectedTableContent();
						charsets.update(charset);
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

					dlg = new GUIOptionCharSetDialog(owner);
					List<CharSet> charsetList = dlg.showDialog();
					for (CharSet charset : charsetList) {

						charsets.create(charset);
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

					CharSet charset = getSelectedTableContent();
					charsets.delete(charset);
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, null, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(CharSet charSet) {
		charsets_list.add(charSet);
		option_model.addRow(new Object[]{charSet.getCharSetName()});
	}

	@Override
	protected void updateTable(List<CharSet> charsetList) {
		clearTableContents();
		for (CharSet charset : charsetList) {

			addTableContent(charset);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(charsets.queryAll());
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	protected void clearTableContents() {
		charsets_list.clear();
		option_model.setRowCount(0);
	}

	@Override
	protected CharSet getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected CharSet getTableContent(int rowIndex) {
		return charsets_list.get(rowIndex);
	}
}
