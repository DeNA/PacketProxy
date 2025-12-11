/*
 * Copyright 2021 DeNA Co., Ltd.
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

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import packetproxy.common.Range;
import packetproxy.model.OneShotPacket;
import packetproxy.vulchecker.VulChecker;

public class GUIVulCheckHelper {

	private JPanel main_panel;
	private CloseButtonTabbedPane vulCheckTab;
	private List<GUIVulCheckTab> list;
	private int previousTabIndex;

	private static GUIVulCheckHelper instance;

	public static GUIVulCheckHelper getInstance() throws Exception {
		if (instance == null) {

			instance = new GUIVulCheckHelper();
		}
		return instance;
	}

	private GUIVulCheckHelper() {
		main_panel = new JPanel();
		vulCheckTab = new CloseButtonTabbedPane();
		previousTabIndex = vulCheckTab.getSelectedIndex();
		vulCheckTab.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				int currentTabIndex = vulCheckTab.getSelectedIndex();
				if (previousTabIndex < 0) {

					previousTabIndex = currentTabIndex;
					return;
				}
				previousTabIndex = currentTabIndex;
			}
		});
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		main_panel.add(vulCheckTab);
		list = new ArrayList<GUIVulCheckTab>();
	}

	public JComponent createPanel() {
		return main_panel;
	}

	public void addVulCheck(VulChecker vulChecker, OneShotPacket send_packet, Range range) throws Exception {
		GUIVulCheckTab vulCheckTab = new GUIVulCheckTab(vulChecker, send_packet, range);
		JComponent panel = vulCheckTab.createPanel();
		list.add(vulCheckTab);
		this.vulCheckTab.addTab(String.valueOf(list.size()), panel);
		this.vulCheckTab.setSelectedComponent(panel);
	}
}
