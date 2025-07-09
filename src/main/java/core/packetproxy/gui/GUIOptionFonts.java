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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;

public class GUIOptionFonts {
	private JFrame owner;
	private JTextField uiFontInfo;
	private JTextField fontInfo;

	public GUIOptionFonts(JFrame owner) throws Exception {
		this.owner = owner;
	}

	public JPanel createPanel() throws Exception {

		uiFontInfo = new JTextField(String.format("%s (size: %d)", FontManager.getInstance().getUIFont().getName(),
				FontManager.getInstance().getUIFont().getSize()));
		uiFontInfo.setEditable(false);
		uiFontInfo.setMaximumSize(new Dimension(Short.MAX_VALUE, uiFontInfo.getMinimumSize().height));

		JButton uiButton = new JButton(I18nString.get("choose..."));
		uiButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				try {
					JFontChooser jfc = new JFontChooser(FontManager.getInstance().getUIFont());
					int result = jfc.showDialog(owner);
					if (result == JFontChooser.OK_OPTION) {
						Font font = jfc.getSelectedFont();
						FontManager.getInstance().setUIFont(font);
						uiFontInfo
								.setText(String.format("%s (size: %d)", FontManager.getInstance().getUIFont().getName(),
										FontManager.getInstance().getUIFont().getSize()));
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JButton uiRestore = new JButton(I18nString.get("restore default"));
		uiRestore.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				try {
					FontManager.getInstance().restoreUIFont();
					uiFontInfo.setText(String.format("%s (size: %d)", FontManager.getInstance().getUIFont().getName(),
							FontManager.getInstance().getUIFont().getSize()));
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JPanel uiPanel = new JPanel();
		uiPanel.setBackground(Color.WHITE);
		uiPanel.setLayout(new BoxLayout(uiPanel, BoxLayout.X_AXIS));
		uiPanel.add(new JLabel(I18nString.get("UI Font (need a reboot to apply):")));
		uiPanel.add(uiFontInfo);
		uiPanel.add(uiButton);
		uiPanel.add(uiRestore);

		fontInfo = new JTextField(String.format("%s (size: %d)", FontManager.getInstance().getFont().getName(),
				FontManager.getInstance().getFont().getSize()));
		fontInfo.setEditable(false);
		fontInfo.setMaximumSize(new Dimension(Short.MAX_VALUE, fontInfo.getMinimumSize().height));

		JButton button = new JButton(I18nString.get("choose..."));
		button.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				try {
					JFontChooser jfc = new JFontChooser(FontManager.getInstance().getFont());
					int result = jfc.showDialog(owner);
					if (result == JFontChooser.OK_OPTION) {
						Font font = jfc.getSelectedFont();
						FontManager.getInstance().setFont(font);
						fontInfo.setText(String.format("%s (size: %d)", FontManager.getInstance().getFont().getName(),
								FontManager.getInstance().getFont().getSize()));
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JButton restore = new JButton(I18nString.get("restore default"));
		restore.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				try {
					FontManager.getInstance().restoreFont();
					fontInfo.setText(String.format("%s (size: %d)", FontManager.getInstance().getFont().getName(),
							FontManager.getInstance().getFont().getSize()));
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JPanel fPanel = new JPanel();
		fPanel.setBackground(Color.WHITE);
		fPanel.setLayout(new BoxLayout(fPanel, BoxLayout.X_AXIS));
		fPanel.add(new JLabel(I18nString.get("Data Font:")));
		fPanel.add(fontInfo);
		fPanel.add(button);
		fPanel.add(restore);

		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(uiPanel);
		panel.add(fPanel);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		return panel;
	}
}
