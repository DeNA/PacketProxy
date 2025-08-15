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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import packetproxy.common.I18nString;
import packetproxy.model.ConfigString;

public class GUIOptionHttp {

	private JComboBox<String> combo = new JComboBox<>();
	private ConfigString configPriority = new ConfigString("PriorityOrderOfHttpVersions");

	public GUIOptionHttp() throws Exception {
		combo.setPrototypeDisplayValue("xxxxxxx");
		combo.addItem("HTTP1");
		combo.addItem("HTTP2");
		combo.setMaximumRowCount(combo.getItemCount());
		String priority = configPriority.getString();
		if (priority == null || priority.isEmpty()) {

			configPriority.setString("HTTP2");
			priority = configPriority.getString();
		}
		combo.setSelectedItem(priority);
		combo.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				try {

					if (event.getStateChange() != ItemEvent.SELECTED || combo.getSelectedItem() == null) {

						return;
					}
					String priority = (String) combo.getSelectedItem();
					configPriority.setString(priority);
					combo.setSelectedItem(priority);
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		combo.setMaximumSize(new Dimension(combo.getPreferredSize().width, combo.getMinimumSize().height));
	}

	public JPanel createPanel() throws Exception {
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(combo);
		panel.add(new JLabel(I18nString.get("has a high priority")));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, panel.getMaximumSize().height));
		return panel;
	}

}
