/*
 * Copyright 2026 DeNA Co., Ltd.
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

import static packetproxy.model.PropertyChangeEventType.SESSION_PROFILES;
import static packetproxy.util.Logging.errWithStackTrace;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JFrame;
import packetproxy.model.SessionProfile;
import packetproxy.model.SessionProfiles;

public class GUIOptionSessionProfile extends GUIOptionComponentBase<SessionProfile> {

	private SessionProfiles sessionProfiles;
	private List<SessionProfile> tableList;
	private Supplier<String> authorizationSupplier;

	public GUIOptionSessionProfile(JFrame owner) throws Exception {
		this(owner, null);
	}

	public GUIOptionSessionProfile(JFrame owner, Supplier<String> authorizationSupplier) throws Exception {
		super(owner);
		this.authorizationSupplier = authorizationSupplier;
		sessionProfiles = SessionProfiles.getInstance();
		sessionProfiles.addPropertyChangeListener(this);
		tableList = new ArrayList<>();

		String[] menu = {"Name", "Authorization"};
		int[] menuWidth = {150, 400};

		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				var rowIndex = table.rowAtPoint(e.getPoint());
				if (rowIndex >= 0) {
					table.setRowSelectionInterval(rowIndex, rowIndex);
				}
			}
		};
		ActionListener addAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					var dlg = new GUIOptionSessionProfileDialog(owner, authorizationSupplier);
					dlg.showDialog();
				} catch (Exception e1) {
					errWithStackTrace(e1);
				}
			}
		};
		ActionListener editAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					var oldProfile = getSelectedTableContent();
					if (oldProfile == null) {
						return;
					}
					var dlg = new GUIOptionSessionProfileDialog(owner, authorizationSupplier);
					dlg.showDialog(oldProfile);
				} catch (Exception e1) {
					errWithStackTrace(e1);
				}
			}
		};
		ActionListener removeAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					var selected = getSelectedTableContent();
					if (selected == null) {
						return;
					}
					sessionProfiles.delete(selected);
				} catch (Exception e1) {
					errWithStackTrace(e1);
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();
	}

	public void showManageDialog() {
		try {
			var dlg = new GUIOptionSessionProfileDialog(owner, authorizationSupplier);
			dlg.showDialog();
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	@Override
	public void propertyChange(java.beans.PropertyChangeEvent evt) {
		if (SESSION_PROFILES.matches(evt)) {
			updateImpl();
			return;
		}
		super.propertyChange(evt);
	}

	@Override
	protected void addTableContent(SessionProfile profile) {
		tableList.add(profile);
		option_model.addRow(new Object[]{profile.getName(),
				SessionProfile.formatAuthorizationPreview(profile.getAuthorization()),});
	}

	@Override
	protected void updateTable(List<SessionProfile> profileList) {
		clearTableContents();
		for (var profile : profileList) {
			addTableContent(profile);
		}
	}

	@Override
	protected void updateImpl() {
		try {
			updateTable(sessionProfiles.queryAll());
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	@Override
	protected void clearTableContents() {
		option_model.setRowCount(0);
		tableList.clear();
	}

	@Override
	protected SessionProfile getSelectedTableContent() {
		var rowIndex = table.getSelectedRow();
		if (rowIndex < 0) {
			return null;
		}
		return getTableContent(rowIndex);
	}

	@Override
	protected SessionProfile getTableContent(int rowIndex) {
		return tableList.get(rowIndex);
	}
}
