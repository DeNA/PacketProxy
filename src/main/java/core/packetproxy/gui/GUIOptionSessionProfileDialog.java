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

import static packetproxy.util.Logging.errWithStackTrace;

import java.awt.Container;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import packetproxy.common.I18nString;
import packetproxy.model.SessionProfile;

public class GUIOptionSessionProfileDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final JFrame owner;
	private final Supplier<String> authorizationSupplier;

	private final JButton buttonCancel = new JButton(I18nString.get("Cancel"));
	private final JButton buttonSet = new JButton(I18nString.get("Save"));
	private final JTextField nameField = new JTextField();
	private final JTextField authorizationField = new JTextField();

	private SessionProfile profile;

	public GUIOptionSessionProfileDialog(JFrame owner, Supplier<String> authorizationSupplier) throws Exception {
		super(owner);
		this.owner = owner;
		this.authorizationSupplier = authorizationSupplier;
		buildDialog();
	}

	public SessionProfile showDialog() {
		nameField.setText("");
		authorizationField.setText("");
		setModal(true);
		setVisible(true);
		return profile;
	}

	public SessionProfile showDialog(String initialAuthorization) {
		nameField.setText("");
		authorizationField.setText(initialAuthorization != null ? initialAuthorization : "");
		setModal(true);
		setVisible(true);
		return profile;
	}

	public SessionProfile showDialog(SessionProfile preset) {
		nameField.setText(preset.getName());
		authorizationField.setText(preset.getAuthorization() != null ? preset.getAuthorization() : "");
		setModal(true);
		setVisible(true);
		return profile;
	}

	private JComponent labelAndObject(String labelName, JComponent object) {
		var panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		var label = new JLabel(labelName);
		label.setPreferredSize(new Dimension(240, label.getMaximumSize().height));
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
		panel.add(object);
		return panel;
	}

	private JComponent buttons() {
		var panelButton = new JPanel();
		panelButton.setLayout(new BoxLayout(panelButton, BoxLayout.X_AXIS));
		panelButton.setMaximumSize(new Dimension(Short.MAX_VALUE, buttonSet.getMaximumSize().height));
		panelButton.add(buttonCancel);
		panelButton.add(buttonSet);
		return panelButton;
	}

	private void buildDialog() {
		setTitle(I18nString.get("Session Profile"));
		var rect = owner.getBounds();
		var height = 220;
		var width = 800;
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height);

		Container c = getContentPane();
		var panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(labelAndObject("Name:", nameField));
		panel.add(labelAndObject("Authorization:", authorizationField));

		if (authorizationSupplier != null) {
			var importButton = new JButton("Import from current request");
			importButton.addActionListener(e -> importAuthorizationFromRequest());
			panel.add(importButton);
		}

		panel.add(buttons());
		c.add(panel);

		buttonCancel.addActionListener(e -> {
			profile = null;
			dispose();
		});

		buttonSet.addActionListener(e -> saveProfile());
	}

	private void importAuthorizationFromRequest() {
		try {
			var authorization = authorizationSupplier.get();
			if (authorization == null || authorization.isEmpty()) {
				JOptionPane.showMessageDialog(owner, "No Authorization header found in the current request.",
						I18nString.get("Message"), JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			authorizationField.setText(authorization);
		} catch (Exception e) {
			errWithStackTrace(e);
		}
	}

	private void saveProfile() {
		var name = nameField.getText().trim();
		if (name.isEmpty()) {
			JOptionPane.showMessageDialog(owner, "Name is required.", I18nString.get("Message"),
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		profile = new SessionProfile(name, authorizationField.getText());
		dispose();
	}
}
