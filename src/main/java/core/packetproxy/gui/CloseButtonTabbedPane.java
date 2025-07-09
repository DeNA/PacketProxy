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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public class CloseButtonTabbedPane extends JTabbedPane {
	private static final long serialVersionUID = 1L;
	private final ImageIcon icon;
	private final ImageIcon icon_mouseovered;

	public CloseButtonTabbedPane() {
		super();
		icon = new ImageIcon(getClass().getResource("/gui/close.png"));
		icon_mouseovered = new ImageIcon(getClass().getResource("/gui/close_mouseovered.png"));
	}

	@Override
	public void addTab(String title, Component content) {
		JPanel main_panel = new JPanel();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.X_AXIS));
		main_panel.setOpaque(false);
		JLabel label = new JLabel(title);
		JButton button = new JButton(icon);
		button.setPreferredSize(new Dimension(icon.getIconWidth() + 1, icon.getIconHeight() + 1));
		button.setMinimumSize(new Dimension(icon.getIconWidth() + 1, icon.getIconHeight() + 1));
		button.setMaximumSize(new Dimension(icon.getIconWidth() + 1, icon.getIconHeight() + 1));
		button.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				removeTabAt(indexOfComponent(content));
			}
			@Override
			public void mouseEntered(MouseEvent e) {
				button.setIcon(icon_mouseovered);
			}
			@Override
			public void mouseExited(MouseEvent e) {
				button.setIcon(icon);
			}
		});
		main_panel.add(label);
		main_panel.add(button);
		super.addTab(null, content);
		setTabComponentAt(getTabCount() - 1, main_panel);
	}

}
