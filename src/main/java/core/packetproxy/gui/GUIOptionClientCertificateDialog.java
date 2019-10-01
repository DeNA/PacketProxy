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

import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.Objects;

import packetproxy.model.ClientCertificate;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class GUIOptionClientCertificateDialog extends JDialog
{
	private static final long serialVersionUID = 1L;

	private JButton buttonCancel = new JButton("キャンセル");
	private JButton buttonSet = new JButton("保存");

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
		label.setPreferredSize(new Dimension(120, label.getMaximumSize().height));
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

    public ClientCertificate showDialog() {
        setModal(true);
        setVisible(true);
        return certificate;
    }
	public ClientCertificate showDialog(ClientCertificate preset) throws Exception {
	    certificateTypeCombo.setSelectedItem(preset.getType().getText());
	    certificatePathField.setText(preset.getPath());
	    serverCombo.setSelectedItem(preset.getServerName());

		setModal(true);
		setVisible(true);
		return certificate;
	}

	private JComponent createCertificateTypeSetting() {
	    for (ClientCertificate.Type t: ClientCertificate.Type.values()) {
			certificateTypeCombo.addItem(t.getText());
		}
        certificateTypeCombo.setSelectedItem(null);
		certificateTypeCombo.setMaximumRowCount(ClientCertificate.Type.values().length);
	    certificateTypeCombo.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				String t = (String) e.getItem();
				certFilePath.resetChoosableFileFilters();
				switch (Objects.requireNonNull(ClientCertificate.Type.getTypeFromText(t))){
					case JKS:
						keyPasswordField.setEnabled(true);
						certFilePath.addChoosableFileFilter(new FileNameExtensionFilter("証明書ファイル (JKS)", "jks"));
						break;
					case P12:
						keyPasswordField.setEnabled(false);
						certFilePath.addChoosableFileFilter(new FileNameExtensionFilter("証明書ファイル (PKCS#12)", "p12", "pfx"));
						break;
					default:
				}
			}
		});

		return label_and_object("証明書の種類:", certificateTypeCombo);
	}
	private JComponent createCertificatePathSetting() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		JButton button = new JButton("参照");
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

		return label_and_object("証明書:", panel);
	}
	private JComponent createStorePasswordSetting() {
		return label_and_object("証明書のパスワード:", storePasswordField);
	}
	private JComponent createKeyPasswordSetting() {
		keyPasswordField.setEnabled(false);
		return label_and_object("秘密鍵のパスワード:", keyPasswordField);
	}
	private JComponent createAppliedServers() throws Exception {
		// TODO: 任意のホスト名も選べるようにする
		List<Server> servers = Servers.getInstance().queryAll();
		for (Server server : servers) {
			serverCombo.addItem(server.toString());
		}
		serverCombo.setEnabled(true);
		serverCombo.setMaximumRowCount(servers.size());
		return label_and_object("適用サーバ:", serverCombo);
	}

	GUIOptionClientCertificateDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle("設定");
		Rectangle rect = owner.getBounds();
		int height = 500;
		int width = 500;
		setBounds(rect.x + rect.width/2 - width /2, rect.y + rect.height/2 - width /2, width, height); /* ド真ん中 */

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

				certificate = ClientCertificate.convert(type,
						Servers.getInstance().queryByString(serverName),
						certificatePathField.getText(),
						storePasswordField.getPassword(),
						keyPasswordField.getPassword());

				dispose();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		});
	}
}
