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
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import packetproxy.common.I18nString;
import packetproxy.model.Modification;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class GUIOptionModificationDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private JTextField text_pattern = new JTextField();
	private JTextField text_replaced = new JTextField();
	JComboBox<String> method_combo = new JComboBox<String>();
	JComboBox<String> server_combo = new JComboBox<String>();
	JComboBox<String> direction_combo = new JComboBox<String>();
	private int height = 500;
	private int width = 500;
	private Modification modification = null;

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

	public Modification showDialog(Modification preset) throws Exception {
		text_pattern.setText(preset.getPattern());
		text_replaced.setText(preset.getReplaced());
		method_combo.setSelectedItem(preset.getMethod().toString());
		direction_combo.setSelectedItem(preset.getDirection().toString());
		server_combo.setSelectedItem(preset.getServerName());
		setModal(true);
		setVisible(true);
		return modification;
	}

	public Modification showDialog() {
		setModal(true);
		setVisible(true);
		return modification;
	}

	private JComponent createAppliedServers() throws Exception {
		server_combo.addItem("*");
		List<Server> servers = Servers.getInstance().queryAll();
		for (Server server : servers) {

			server_combo.addItem(server.toString());
		}
		server_combo.setEnabled(true);
		server_combo.setMaximumRowCount(servers.size());
		return label_and_object(I18nString.get("Applied server:"), server_combo);
	}

	private JComponent createTypeSetting() {
		direction_combo.addItem("CLIENT_REQUEST");
		direction_combo.addItem("SERVER_RESPONSE");
		direction_combo.addItem("ALL");
		direction_combo.setEnabled(true);
		direction_combo.setMaximumRowCount(3);
		return label_and_object("Direction:", direction_combo);
	}

	private JComponent createReplaceMethodSetting() {
		method_combo.addItem("SIMPLE");
		method_combo.addItem("REGEX");
		method_combo.addItem("BINARY");
		method_combo.setEnabled(true);
		method_combo.setMaximumRowCount(3);
		return label_and_object("Method:", method_combo);
	}

	private JComponent createPatternSetting() {
		return label_and_object("Pattern:", text_pattern);
	}

	private JComponent createReplacedSetting() {
		return label_and_object("Replaced:", text_replaced);
	}

	public GUIOptionModificationDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle(I18nString.get("Setting"));
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height); /* ド真ん中 */

		// text_pattern.addKeyListener(new KeyAdapter() {
		// @Override
		// public void keyReleased(KeyEvent arg0) {
		// String str = text_pattern.getText();
		// try {
		// int a = text_pattern.getCaretPosition();
		// Binary b = new Binary(new Binary.HexString(str));
		// text_pattern.setText(b.toHexString(16).toString());
		// text_pattern.setCaretPosition(a);
		// } catch (Exception e1) {
		// }
		// }
		// });

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createReplaceMethodSetting());
		panel.add(createPatternSetting());
		panel.add(createReplacedSetting());
		panel.add(createTypeSetting());
		panel.add(createAppliedServers());

		panel.add(buttons());

		c.add(panel);

		button_cancel.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				modification = null;
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					Modification.Direction type = null;
					if (direction_combo.getSelectedItem().toString().equals("CLIENT_REQUEST")) {

						type = Modification.Direction.CLIENT_REQUEST;
					} else if (direction_combo.getSelectedItem().toString().equals("SERVER_RESPONSE")) {

						type = Modification.Direction.SERVER_RESPONSE;
					} else {

						type = Modification.Direction.ALL;
					}
					Modification.Method method = null;
					if (method_combo.getSelectedItem().toString().equals("SIMPLE")) {

						method = Modification.Method.SIMPLE;
					} else if (method_combo.getSelectedItem().toString().equals("REGEX")) {

						method = Modification.Method.REGEX;
					} else {

						method = Modification.Method.BINARY;
					}

					String server_str = server_combo.getSelectedItem().toString();
					modification = new Modification(type, text_pattern.getText(), text_replaced.getText(), method,
							Servers.getInstance().queryByString(server_str));
					dispose();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
	}
}
