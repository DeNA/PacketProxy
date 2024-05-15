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
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import packetproxy.common.I18nString;
import packetproxy.model.ClientCertificate;
import packetproxy.model.ClientCertificates;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class GUIOptionClientCertificateDialog extends JDialog
{
	private static final long serialVersionUID = 1L;

	private JFrame owner;

	private JButton buttonCancel = new JButton(I18nString.get("Cancel"));
	private JButton buttonSet = new JButton(I18nString.get("Save"));

	private JComboBox<String> certificateTypeCombo = new JComboBox<String>();
	private JTextField certificatePathField = new JTextField();
    private JFileChooser certFilePath = new JFileChooser();
    private JPasswordField storePasswordField = new JPasswordField();
	private JPasswordField keyPasswordField = new JPasswordField();
	private JComboBox<String> serverCombo = new JComboBox<String>();

	private ClientCertificate certificate = null;

	private JComponent label_and_object(String label_name, JComponent object) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(240, label.getMaximumSize().height));
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
		panel.add(object);
		return panel;
	}
	private JComponent buttons() {
		JPanel panel_button = new JPanel();
		panel_button.setLayout(new BoxLayout(panel_button, BoxLayout.X_AXIS));
		panel_button.setMaximumSize(new Dimension(Short.MAX_VALUE, buttonSet.getMaximumSize().height));
		panel_button.add(buttonCancel);
		panel_button.add(buttonSet);
		return panel_button;
	}

    public ClientCertificate showDialog() throws Exception {
		if (Servers.getInstance().queryAll().isEmpty()) {
			JOptionPane.showMessageDialog(this.owner,
					I18nString.get("Set server you wish to connect into 'Servers setting' first."),
					I18nString.get("Message"),
					JOptionPane.INFORMATION_MESSAGE);
			certificate = null;
		} else {
			setModal(true);
        	setVisible(true);
		}
        return certificate;
    }

	public ClientCertificate showDialog(ClientCertificate preset) throws Exception {
	    certificateTypeCombo.setSelectedItem(preset.getType().getText());
	    certificatePathField.setText(preset.getPath());
	    storePasswordField.setText(preset.getStorePassword());
	    keyPasswordField.setText(preset.getKeyPassword());
	    serverCombo.setSelectedItem(preset.getServerName());

		setModal(true);
		setVisible(true);
		return certificate;
	}

	private JComponent createCertificateTypeSetting() {
	    for (ClientCertificate.Type t: ClientCertificate.Type.values()) {
			certificateTypeCombo.addItem(t.getText());
		}
        certificateTypeCombo.setSelectedIndex(0); /* default p12 */
		certificateTypeCombo.setMaximumRowCount(ClientCertificate.Type.values().length);
	    certificateTypeCombo.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				String t = (String) e.getItem();
				certFilePath.resetChoosableFileFilters();
				switch (ClientCertificate.Type.getTypeFromText(t)){
					case JKS:
						certFilePath.addChoosableFileFilter(new FileNameExtensionFilter(I18nString.get("Client Certificate file (*.jks)"), "jks"));
						break;
					case P12:
						certFilePath.addChoosableFileFilter(new FileNameExtensionFilter(I18nString.get("Client Certificate file (*.p12, *.pfx)"), "p12", "pfx"));
						break;
					default:
				}
			}
		});
		certFilePath.addChoosableFileFilter(new FileNameExtensionFilter(I18nString.get("Client Certificate file (*.p12, *.pfx)"), "p12", "pfx"));

		return label_and_object(I18nString.get("Type of certificate file:"), certificateTypeCombo);
	}
	private JComponent createCertificatePathSetting() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		JButton button = new JButton(I18nString.get("choose..."));
		button.addActionListener(arg0 -> {
			try {
				certFilePath.setAcceptAllFileFilterUsed(false);
				certFilePath.showOpenDialog(panel);
				File file = certFilePath.getSelectedFile();
				if (file != null) {
					certificatePathField.setText(file.getPath());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

        panel.add(certificatePathField);
        panel.add(button);

		return label_and_object(I18nString.get("Certificate file:"), panel);
	}
	private JComponent createStorePasswordSetting() {
		return label_and_object(I18nString.get("Password of the certifacate file:"), storePasswordField);
	}
	private JComponent createKeyPasswordSetting() {
		keyPasswordField.setEnabled(true);
		return label_and_object(I18nString.get("Password of the secret key:"), keyPasswordField);
	}
	private JComponent createAppliedServers() throws Exception {
		// TODO: 任意のホスト名も選べるようにする
		List<Server> servers = Servers.getInstance().queryAll();
		for (Server server : servers) {
			serverCombo.addItem(server.toString());
		}
		serverCombo.setEnabled(true);
		serverCombo.setMaximumRowCount(servers.size());
		return label_and_object(I18nString.get("Applied server:"), serverCombo);
	}

	GUIOptionClientCertificateDialog(JFrame owner) throws Exception {
		super(owner);
		this.owner = owner;
		setTitle(I18nString.get("Setting"));
		Rectangle rect = owner.getBounds();
		int height = 500;
		int width = 800;
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - height/2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel();
	    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(createCertificateTypeSetting());
		panel.add(createCertificatePathSetting());
		panel.add(createStorePasswordSetting());
        panel.add(createKeyPasswordSetting());
	    panel.add(createAppliedServers());
	    panel.add(buttons());
		c.add(panel);

	    buttonCancel.addActionListener(e -> {
			certificate = null;
			dispose();
		});

		buttonSet.addActionListener(e -> {
			try {
				ClientCertificate.Type type =
						ClientCertificate.Type.getTypeFromText(Objects.requireNonNull(certificateTypeCombo.getSelectedItem()).toString());
				String serverName = (String) serverCombo.getSelectedItem();

				assert(type != null);
				assert(serverName != null);

				try {
					certificate = ClientCertificate.convert(type,
						Servers.getInstance().queryByString(serverName),
						certificatePathField.getText(),
						storePasswordField.getPassword(),
						keyPasswordField.getPassword());
				} catch (Exception e2) {
					certificate = null;
					JOptionPane.showMessageDialog(this.owner,
							I18nString.get("[Error] incorrect certificate file password."),
							I18nString.get("Message"),
							JOptionPane.INFORMATION_MESSAGE);
					return;
				}

				if (ClientCertificates.getInstance().hasCorrectSecretKey(certificate) == false) {
					certificate = null;
					JOptionPane.showMessageDialog(this.owner,
							I18nString.get("[Error] incorrect secret key password."),
							I18nString.get("Message"),
							JOptionPane.INFORMATION_MESSAGE);
					return;
				}

				dispose();

			} catch (Exception e1) {
				e1.printStackTrace();
			}
		});
	}
}
