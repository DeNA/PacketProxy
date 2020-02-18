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

import packetproxy.model.MockResponse;
import packetproxy.model.MockResponses;
import packetproxy.model.Server;
import packetproxy.model.Servers;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class GUIOptionMockResponses extends GUIOptionComponentBase<MockResponse>
{
	private GUIOptionMockResponseDialog dlg;
	private MockResponses mock_responses;
	private List<MockResponse> mock_responses_list;
	public GUIOptionMockResponses(JFrame owner) throws Exception {
		super(owner);
		mock_responses = MockResponses.getInstance();
		mock_responses.addObserver(this);
		mock_responses_list = new ArrayList<MockResponse>();
		String[] menu = { "Enabled", "Host", "Port", "Path", "Mock Response", "Comment" };
		int[] menuWidth = { 50, 200, 80, 100, 160, 160 };
		MouseAdapter tableAction = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					int columnIndex= table.columnAtPoint(e.getPoint());
					int rowIndex= table.rowAtPoint(e.getPoint());
					if (columnIndex == 0) { /* Enabled */
						boolean enable_checkbox = (Boolean)table.getValueAt(rowIndex, 0);
						MockResponse mockres = getSelectedTableContent();
						if (enable_checkbox == true) {
							mockres.setDisabled();
						} else {
							mockres.setEnabled();
						}
						mock_responses.update(mockres);
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
					dlg = new GUIOptionMockResponseDialog(owner);
					MockResponse mockResponse = dlg.showDialog();
					mock_responses.create(mockResponse);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		ActionListener editAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					MockResponse old_mock_response = getSelectedTableContent();
					dlg = new GUIOptionMockResponseDialog(owner);
					MockResponse new_mock_response = dlg.showDialog(old_mock_response);
					MockResponses.getInstance().update(new_mock_response);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		ActionListener removeAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					MockResponse mockResponse = getSelectedTableContent();
					mock_responses.delete(mockResponse);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	@Override
	protected void addTableContent(MockResponse mockResponse) {
		mock_responses_list.add(mockResponse);
		option_model.addRow(new Object[] {
				mockResponse.isEnabled(),
				mockResponse.getIp(),
				mockResponse.getPort(),
				mockResponse.getPath(),
				mockResponse.getMockResponse(),
				mockResponse.getComment()
		});
	}
	@Override
	protected void updateTable(List<MockResponse> mockResponseList) {
		clearTableContents();
		for (MockResponse mockResponse : mockResponseList) {
			addTableContent(mockResponse);
		}
	}

	@Override
	protected void updateImpl() {
		try {
			updateTable(mock_responses.queryAll());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void clearTableContents() {
		mock_responses_list.clear();
		option_model.setRowCount(0);
	}

	@Override
	protected MockResponse getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected MockResponse getTableContent(int rowIndex) {
		return mock_responses_list.get(rowIndex);
	}
}
