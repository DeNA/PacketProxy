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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import packetproxy.common.I18nString;
import packetproxy.model.SSLPassThrough;

public class GUIOptionSSLPassThroughDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private HintTextField serverNameField = new HintTextField(".*\\.apple\\.com");
	private HintTextField portField = new HintTextField(I18nString.get("Use * to apply all ports"));
	private int height = 500;
	private int width = 500;
	private SSLPassThrough sslPassThrough = null;

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
	public SSLPassThrough showDialog(SSLPassThrough preset) throws Exception {
		serverNameField.setText(preset.getServerName());
		String portStr = (preset.getListenPort() == SSLPassThrough.ALL_PORTS)
				? "*"
				: String.valueOf(preset.getListenPort());
		portField.setText(portStr);
		setModal(true);
		setVisible(true);
		return sslPassThrough;
	}
	public SSLPassThrough showDialog() {
		setModal(true);
		setVisible(true);
		return sslPassThrough;
	}
	private JComponent createAppliedListenPort() throws Exception {
		return label_and_object(I18nString.get("Target listen port:"), portField);
	}
	private JComponent createServerNameSetting() {
		return label_and_object(I18nString.get("Target server name:"), serverNameField);
	}
	public GUIOptionSSLPassThroughDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle(I18nString.get("Setting"));
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createServerNameSetting());
		panel.add(createAppliedListenPort());

		panel.add(buttons());

		c.add(panel);

		button_cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sslPassThrough = null;
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					String serverName = serverNameField.getText();
					String portStr = portField.getText();
					int port = (portStr.matches("\\*")) ? -1 : Integer.parseInt(portStr);
					sslPassThrough = new SSLPassThrough(serverName, port);
					dispose();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
	}
}
