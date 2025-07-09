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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.*;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import packetproxy.common.I18nString;
import packetproxy.model.Resolution;

public class GUIOptionResolutionDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private HintTextField text_ip = new HintTextField("(ex.) 127.0.0.1");
	private HintTextField text_hostname = new HintTextField("(ex.) example.com");
	private HintTextField text_comment = new HintTextField("(ex.) game server for test");
	private JCheckBox checkbox_enabled = new JCheckBox(I18nString.get("Override"));
	private int height = 500;
	private int width = 700;
	private Resolution resolution = null;

	private JComponent label_and_object(String label_name, JComponent object) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(150, label.getMaximumSize().height));
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
		panel.add(object);
		return panel;
	}
	private JComponent buttons() {
		JPanel panel_button = new JPanel();
		panel_button.setLayout(new BoxLayout(panel_button, BoxLayout.X_AXIS));
		panel_button.setMaximumSize(new Dimension(Short.MAX_VALUE, button_set.getMaximumSize().height));
		panel_button.add(button_cancel);
		panel_button.add(button_set);
		return panel_button;
	}
	public Resolution showDialog(Resolution preset) {
		text_ip.setText(preset.getIp());
		text_hostname.setText(preset.getHostName());
		checkbox_enabled.setSelected(preset.isEnabled());
		text_comment.setText(preset.getComment());
		setModal(true);
		setVisible(true);
		if (resolution != null) {
			preset.setIp(text_ip.getText());
			preset.setHostName(text_hostname.getText());
			preset.setEnabled(checkbox_enabled.isEnabled());
			preset.setComment(text_comment.getText());
			return preset;
		}
		return resolution;
	}
	public Resolution showDialog() {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				button_cancel.requestFocusInWindow();
			}
		});
		setModal(true);
		setVisible(true);
		return resolution;
	}

	private JComponent createIpSetting() {
		return label_and_object(I18nString.get("ip:"), text_ip);
	}
	private JComponent createHostNameSetting() {
		return label_and_object(I18nString.get("host:"), text_hostname);
	}
	private JComponent createEnabledSetting() {
		return label_and_object(I18nString.get("enabled:"), checkbox_enabled);
	}
	private JComponent createCommentSetting() {
		return label_and_object(I18nString.get("Comments:"), text_comment);
	}
	public GUIOptionResolutionDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle(I18nString.get("Resolution setting"));
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createIpSetting());
		panel.add(createHostNameSetting());
		panel.add(createEnabledSetting());
		panel.add(createCommentSetting());

		panel.add(buttons());

		c.add(panel);

		button_cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				resolution = null;
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				resolution = new Resolution(text_ip.getText(), text_hostname.getText(), checkbox_enabled.isSelected(),
						text_comment.getText());
				dispose();
			}
		});
	}
}
