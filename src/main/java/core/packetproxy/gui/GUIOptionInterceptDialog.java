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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
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
import packetproxy.model.InterceptOption;
import packetproxy.model.InterceptOption.Direction;
import packetproxy.model.InterceptOption.Method;
import packetproxy.model.InterceptOption.Relationship;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class GUIOptionInterceptDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private JComboBox<String> direction_combo = new JComboBox<String>();
	private JComboBox<String> relationship_combo = new JComboBox<String>();
	private JComboBox<String> method_combo = new JComboBox<String>();
	private JTextField text_pattern = new JTextField();
	private JComboBox<String> server_combo = new JComboBox<String>();
	private int height = 500;
	private int width = 500;
	private InterceptOption intercept_option = null;

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

	public InterceptOption showDialog(InterceptOption preset) throws Exception {
		if (preset.getDirection() == Direction.ALL_THE_OTHER_REQUESTS
				|| preset.getDirection() == Direction.ALL_THE_OTHER_RESPONSES) {
			direction_combo.removeAllItems();
			direction_combo.addItem(preset.getDirectionAsString());
			direction_combo.setSelectedIndex(0);
			relationship_combo.removeAllItems();
			relationship_combo.addItem(InterceptOption.getRelationshipAsString(Relationship.ARE_INTERCEPTED));
			relationship_combo.addItem(InterceptOption.getRelationshipAsString(Relationship.ARE_NOT_INTERCEPTED));
			relationship_combo.setSelectedItem(preset.getRelationshipAsString());
			method_combo.setEnabled(false);
			text_pattern.setEnabled(false);
			server_combo.setEnabled(false);
		} else {
			direction_combo.setSelectedItem(preset.getDirectionAsString());
			relationship_combo.setSelectedItem(preset.getRelationshipAsString());
			method_combo.setSelectedItem(preset.getMethod().toString());
			text_pattern.setText(preset.getPattern());
			server_combo.setSelectedItem(preset.getServerName());
		}
		setModal(true);
		setVisible(true);
		return intercept_option;
	}

	public InterceptOption showDialog() {
		setModal(true);
		setVisible(true);
		return intercept_option;
	}

	private JComponent createDirectionSetting() {
		direction_combo.addItem(InterceptOption.getDirectionAsString(Direction.REQUEST));
		direction_combo.addItem(InterceptOption.getDirectionAsString(Direction.RESPONSE));
		direction_combo.setSelectedIndex(0);
		direction_combo.setEnabled(true);
		direction_combo.setMaximumRowCount(2);
		direction_combo.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				try {
					if (event.getStateChange() != ItemEvent.SELECTED)
						return;
					String selectedItem = (String) event.getItem();
					updateRelationship(selectedItem);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		return label_and_object(I18nString.get("Direction:"), direction_combo);
	}

	private void updateRelationship(String direction) {
		relationship_combo.removeAllItems();

		if (direction.equals(InterceptOption.getDirectionAsString(Direction.ALL_THE_OTHER_REQUESTS))
				|| direction.equals(InterceptOption.getDirectionAsString(Direction.ALL_THE_OTHER_RESPONSES))) {
			relationship_combo.addItem(InterceptOption.getRelationshipAsString(Relationship.ARE_INTERCEPTED));
			relationship_combo.addItem(InterceptOption.getRelationshipAsString(Relationship.ARE_NOT_INTERCEPTED));
		} else {
			relationship_combo
					.addItem(InterceptOption.getRelationshipAsString(Relationship.IS_INTERCEPTED_IF_IT_MATCHES));
			relationship_combo
					.addItem(InterceptOption.getRelationshipAsString(Relationship.IS_NOT_INTERCEPTED_IF_IT_MATCHES));
			relationship_combo.setMaximumRowCount(2);
			if (direction.equals(InterceptOption.getDirectionAsString(Direction.RESPONSE))) {
				relationship_combo.addItem(InterceptOption
						.getRelationshipAsString(Relationship.IS_INTERCEPTED_IF_REQUEST_WAS_INTERCEPTED));
				relationship_combo.setMaximumRowCount(3);
			}
		}
	}

	private JComponent createRelationshipSetting() {
		relationship_combo.setEnabled(true);
		relationship_combo.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				try {
					if (event.getStateChange() != ItemEvent.SELECTED)
						return;
					String selectedRelationship = (String) event.getItem();
					if (selectedRelationship.equals(InterceptOption
							.getRelationshipAsString(Relationship.IS_INTERCEPTED_IF_REQUEST_WAS_INTERCEPTED))) {
						method_combo.setEnabled(false);
						text_pattern.setEnabled(false);
					} else {
						method_combo.setEnabled(true);
						text_pattern.setEnabled(true);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		updateRelationship("Request");
		return label_and_object(I18nString.get("Action and Condition:"), relationship_combo);
	}

	private JComponent createReplaceMethodSetting() {
		method_combo.addItem("SIMPLE");
		method_combo.addItem("REGEX");
		method_combo.addItem("BINARY");
		method_combo.setEnabled(true);
		method_combo.setMaximumRowCount(3);
		return label_and_object(I18nString.get("Pattern Type:"), method_combo);
	}

	private JComponent createPatternSetting() {
		return label_and_object(I18nString.get("Pattern:"), text_pattern);
	}

	private JComponent createAppliedServers() throws Exception {
		server_combo.addItem("*");
		List<Server> servers = Servers.getInstance().queryAll();
		for (Server server : servers) {
			server_combo.addItem(server.toString());
		}
		server_combo.setEnabled(true);
		server_combo.setMaximumRowCount(servers.size());
		return label_and_object(I18nString.get("Target Server:"), server_combo);
	}

	public GUIOptionInterceptDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle("設定");
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - width / 2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createDirectionSetting());
		panel.add(createRelationshipSetting());
		panel.add(createReplaceMethodSetting());
		panel.add(createPatternSetting());
		panel.add(createAppliedServers());

		panel.add(buttons());

		c.add(panel);

		button_cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				intercept_option = null;
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					Direction direction = InterceptOption.getDirection(direction_combo.getSelectedItem().toString());
					Relationship relationship = InterceptOption
							.getRelationship(relationship_combo.getSelectedItem().toString());
					Method method = null;
					String pattern = "";

					if (relationship == Relationship.IS_INTERCEPTED_IF_REQUEST_WAS_INTERCEPTED) {
						method = Method.UNDEFINED;
						pattern = "";
					} else {
						if (method_combo.getSelectedItem().toString().equals("SIMPLE")) {
							method = Method.SIMPLE;
						} else if (method_combo.getSelectedItem().toString().equals("REGEX")) {
							method = Method.REGEX;
						} else if (method_combo.getSelectedItem().toString().equals("BINARY")) {
							method = Method.BINARY;
						}
						pattern = text_pattern.getText();
					}

					assert (direction != null);
					assert (relationship != null);
					assert (method != null);

					String server_str = server_combo.getSelectedItem().toString();

					intercept_option = new InterceptOption(direction, InterceptOption.Type.REQUEST, relationship,
							pattern, method, Servers.getInstance().queryByString(server_str));
					dispose();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
	}
}
