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

import javax.swing.JOptionPane;
import packetproxy.common.I18nString;
import packetproxy.platform.ConfigHttpUiActions;

public class SwingConfigHttpUiActions implements ConfigHttpUiActions {

	@Override
	public void showOptionsTab() {
		try {
			showOptionsTabInternal();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void showOptionsTabInternal() throws Exception {
		var main = GUIMain.getInstance();
		main.setAlwaysOnTop(true);
		main.setVisible(true);
		main.getTabbedPane().setSelectedIndex(GUIMain.Panes.OPTIONS.ordinal());
		main.setAlwaysOnTop(false);
	}

	@Override
	public boolean confirmOverwriteConfig() {
		try {
			return confirmOverwriteConfigInternal();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean confirmOverwriteConfigInternal() throws Exception {
		var main = GUIMain.getInstance();
		main.setAlwaysOnTop(true);
		main.setVisible(true);
		int option =
				JOptionPane.showConfirmDialog(
						main,
						I18nString.get("Do you want to overwrite config?"),
						I18nString.get("Loading config"),
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE);
		main.setAlwaysOnTop(false);
		return option == JOptionPane.YES_OPTION;
	}
}
