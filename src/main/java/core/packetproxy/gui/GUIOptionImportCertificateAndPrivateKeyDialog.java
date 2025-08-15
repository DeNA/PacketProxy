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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import packetproxy.common.I18nString;
import packetproxy.model.CAs.PacketProxyCAPerUser;

public class GUIOptionImportCertificateAndPrivateKeyDialog extends JDialog {

	private JPanel cardPanel = new JPanel();
	private CardLayout cardLayout = new CardLayout();
	private PacketProxyCAPerUser ca;

	GUIOptionImportCertificateAndPrivateKeyDialog(JFrame owner, PacketProxyCAPerUser ca) {
		super(owner);
		this.ca = ca;
		setTitle(I18nString.get("Import certificate and private key"));
		Rectangle rect = owner.getBounds();
		int height = 500;
		int width = 800;
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		cardPanel.setLayout(cardLayout);

		cardPanel.add(createSelectPanel(), "select panel");
		cardPanel.add(createImportPEMPanel(), "import pem panel");
		cardPanel.add(createImportDERPanel(), "import der panel");
		cardPanel.add(createImportP12Panel(), "import p12 panel");

		c.add(cardPanel);
	}

	private JPanel createSelectPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JLabel chooseImportType = new JLabel(I18nString.get("Choose import type"));
		JLabel importCaution = new JLabel(I18nString
				.get("Importing overwrite PacketProxy per-user CA, so please export in advance if necessary"));
		panel.add(chooseImportType);
		panel.add(importCaution);

		JRadioButton selectPEMButton = new JRadioButton(
				I18nString.get("Certificate(PEM format)+Private Key(PEM format)"));
		JRadioButton selectDERButton = new JRadioButton(
				I18nString.get("Certificate(DER format)+Private Key(DER format)"));
		JRadioButton selectP12Button = new JRadioButton(I18nString.get("Certificate&Private Key(P12 format)"));
		ButtonGroup bgroup = new ButtonGroup();
		bgroup.add(selectPEMButton);
		bgroup.add(selectDERButton);
		bgroup.add(selectP12Button);
		panel.add(selectPEMButton);
		panel.add(selectDERButton);
		panel.add(selectP12Button);

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		JButton selectCancelButton = new JButton(I18nString.get("Cancel"));
		JButton selectDoneButton = new JButton(I18nString.get("Next"));
		buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, selectCancelButton.getMaximumSize().height));
		buttons.add(selectCancelButton);
		buttons.add(selectDoneButton);
		panel.add(buttons);

		selectCancelButton.addActionListener(e -> {

			dispose();
		});
		selectDoneButton.addActionListener(e -> {

			if (selectPEMButton.isSelected())
				cardLayout.show(cardPanel, "import pem panel");
			else if (selectDERButton.isSelected())
				cardLayout.show(cardPanel, "import der panel");
			else if (selectP12Button.isSelected())
				cardLayout.show(cardPanel, "import p12 panel");
		});

		chooseImportType.setAlignmentX(Component.LEFT_ALIGNMENT);
		importCaution.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectPEMButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectDERButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectP12Button.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

		return panel;
	}

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

	private JPanel createImportPEMPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JPanel certificatePanel = new JPanel();
		certificatePanel.setLayout(new BoxLayout(certificatePanel, BoxLayout.X_AXIS));
		JTextField certificatePEMPathField = new JTextField();
		certificatePanel.add(certificatePEMPathField);
		JButton certificateChoosebutton = new JButton(I18nString.get("choose..."));
		JFileChooser certificatePEMChooser = new JFileChooser();
		certificateChoosebutton.addActionListener(arg0 -> {

			try {

				certificatePEMChooser.addChoosableFileFilter(
						new FileNameExtensionFilter(I18nString.get("Certificate file (*.crt, *.pem)"), "crt", "pem"));
				certificatePEMChooser.setAcceptAllFileFilterUsed(false);
				certificatePEMChooser.showOpenDialog(panel);
				File file = certificatePEMChooser.getSelectedFile();
				if (file != null) {

					certificatePEMPathField.setText(file.getPath());
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
		certificatePanel.add(certificateChoosebutton);
		panel.add(label_and_object(I18nString.get("Certificate file:"), certificatePanel));

		JPanel privateKeyPanel = new JPanel();
		privateKeyPanel.setLayout(new BoxLayout(privateKeyPanel, BoxLayout.X_AXIS));
		JTextField privateKeyPEMPathField = new JTextField();
		privateKeyPanel.add(privateKeyPEMPathField);
		JButton privateKeyChoosebutton = new JButton(I18nString.get("choose..."));
		JFileChooser privateKeyPEMChooser = new JFileChooser();
		privateKeyChoosebutton.addActionListener(arg0 -> {

			try {

				privateKeyPEMChooser.addChoosableFileFilter(
						new FileNameExtensionFilter(I18nString.get("Private Key file (*.key, *.pem)"), "key", "pem"));
				privateKeyPEMChooser.setAcceptAllFileFilterUsed(false);
				privateKeyPEMChooser.showOpenDialog(panel);
				File file = privateKeyPEMChooser.getSelectedFile();
				if (file != null) {

					privateKeyPEMPathField.setText(file.getPath());
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
		privateKeyPanel.add(privateKeyChoosebutton);
		panel.add(label_and_object(I18nString.get("Private Key file:"), privateKeyPanel));

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		JButton pemCancelButton = new JButton(I18nString.get("Back"));
		JButton pemChooseDoneButton = new JButton(I18nString.get("Import"));
		buttons.add(pemCancelButton);
		buttons.add(pemChooseDoneButton);
		buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, pemCancelButton.getMaximumSize().height));
		panel.add(buttons);

		pemCancelButton.addActionListener(arg0 -> {

			certificatePEMPathField.setText("");
			privateKeyPEMPathField.setText("");
			cardLayout.show(cardPanel, "select panel");
		});
		pemChooseDoneButton.addActionListener(arg0 -> {

			if (certificatePEMPathField.getText().isEmpty() || privateKeyPEMPathField.getText().isEmpty())
				return;
			try {

				ca.importPEM(certificatePEMPathField.getText(), privateKeyPEMPathField.getText());
				JOptionPane.showMessageDialog(cardPanel, I18nString.get("Successfully imported"));
				dispose();
			} catch (java.nio.file.NoSuchFileException | java.io.FileNotFoundException e) {

				JOptionPane.showMessageDialog(cardPanel, I18nString.get("[Error] no such file."));
			} catch (Exception e) {

				JOptionPane.showMessageDialog(cardPanel, I18nString.get("[Error] failed to import."));
				errWithStackTrace(e);
			}
		});

		return panel;
	}

	private JPanel createImportDERPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JPanel certificatePanel = new JPanel();
		certificatePanel.setLayout(new BoxLayout(certificatePanel, BoxLayout.X_AXIS));
		JTextField certificateDERPathField = new JTextField();
		certificatePanel.add(certificateDERPathField);
		JButton certificateChoosebutton = new JButton(I18nString.get("choose..."));
		JFileChooser certificateDERChooser = new JFileChooser();
		certificateChoosebutton.addActionListener(arg0 -> {

			try {

				certificateDERChooser.addChoosableFileFilter(
						new FileNameExtensionFilter(I18nString.get("Certificate file (*.crt, *.der)"), "crt", "der"));
				certificateDERChooser.setAcceptAllFileFilterUsed(false);
				certificateDERChooser.showOpenDialog(panel);
				File file = certificateDERChooser.getSelectedFile();
				if (file != null) {

					certificateDERPathField.setText(file.getPath());
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
		certificatePanel.add(certificateChoosebutton);
		panel.add(label_and_object(I18nString.get("Certificate file:"), certificatePanel));

		JPanel privateKeyPanel = new JPanel();
		privateKeyPanel.setLayout(new BoxLayout(privateKeyPanel, BoxLayout.X_AXIS));
		JTextField privateKeyDERPathField = new JTextField();
		privateKeyPanel.add(privateKeyDERPathField);
		JButton privateKeyChoosebutton = new JButton(I18nString.get("choose..."));
		JFileChooser privateKeyDERChooser = new JFileChooser();
		privateKeyChoosebutton.addActionListener(arg0 -> {

			try {

				privateKeyDERChooser.addChoosableFileFilter(
						new FileNameExtensionFilter(I18nString.get("Private Key file (*.key, *.der)"), "key", "der"));
				privateKeyDERChooser.setAcceptAllFileFilterUsed(false);
				privateKeyDERChooser.showOpenDialog(panel);
				File file = privateKeyDERChooser.getSelectedFile();
				if (file != null) {

					privateKeyDERPathField.setText(file.getPath());
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
		privateKeyPanel.add(privateKeyChoosebutton);
		panel.add(label_and_object(I18nString.get("Private Key file:"), privateKeyPanel));

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		JButton derCancelButton = new JButton(I18nString.get("Back"));
		JButton derChooseDoneButton = new JButton(I18nString.get("Import"));
		buttons.add(derCancelButton);
		buttons.add(derChooseDoneButton);
		buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, derCancelButton.getMaximumSize().height));
		panel.add(buttons);

		derCancelButton.addActionListener(arg0 -> {

			certificateDERPathField.setText("");
			privateKeyDERPathField.setText("");
			cardLayout.show(cardPanel, "select panel");
		});
		derChooseDoneButton.addActionListener(arg0 -> {

			if (certificateDERPathField.getText().isEmpty() || privateKeyDERPathField.getText().isEmpty())
				return;
			try {

				ca.importDER(certificateDERPathField.getText(), privateKeyDERPathField.getText());
				JOptionPane.showMessageDialog(cardPanel, I18nString.get("Successfully imported"));
				dispose();
			} catch (java.nio.file.NoSuchFileException | java.io.FileNotFoundException e) {

				JOptionPane.showMessageDialog(cardPanel, I18nString.get("[Error] no such file."));
			} catch (Exception e) {

				JOptionPane.showMessageDialog(cardPanel, I18nString.get("[Error] failed to import."));
				errWithStackTrace(e);
			}
		});

		return panel;
	}

	private JPanel createImportP12Panel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JPanel p12Panel = new JPanel();
		p12Panel.setLayout(new BoxLayout(p12Panel, BoxLayout.X_AXIS));
		JTextField p12PathField = new JTextField();
		p12Panel.add(p12PathField);
		JButton p12Choosebutton = new JButton(I18nString.get("choose..."));
		JFileChooser p12Chooser = new JFileChooser();
		p12Choosebutton.addActionListener(arg0 -> {

			try {

				p12Chooser.addChoosableFileFilter(
						new FileNameExtensionFilter(I18nString.get("P12 file (*.p12, *.pfx)"), "p12", "pfx"));
				p12Chooser.setAcceptAllFileFilterUsed(false);
				p12Chooser.showOpenDialog(panel);
				File file = p12Chooser.getSelectedFile();
				if (file != null) {

					p12PathField.setText(file.getPath());
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
		p12Panel.add(p12Choosebutton);
		panel.add(label_and_object(I18nString.get("P12 file:"), p12Panel));

		JPasswordField p12PasswordField = new JPasswordField();
		panel.add(label_and_object(I18nString.get("Password of p12 file:"), p12PasswordField));

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		JButton p12CancelButton = new JButton(I18nString.get("Back"));
		JButton p12ChooseDoneButton = new JButton(I18nString.get("Import"));
		buttons.add(p12CancelButton);
		buttons.add(p12ChooseDoneButton);
		buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, p12CancelButton.getMaximumSize().height));
		panel.add(buttons);

		p12CancelButton.addActionListener(arg0 -> {

			p12PathField.setText("");
			p12PasswordField.setText("");
			cardLayout.show(cardPanel, "select panel");
		});
		p12ChooseDoneButton.addActionListener(arg0 -> {

			if (p12PathField.getText().isEmpty())
				return;
			try {

				ca.importP12(p12PathField.getText(), p12PasswordField.getPassword());
				JOptionPane.showMessageDialog(cardPanel, I18nString.get("Successfully imported"));
				dispose();
			} catch (java.nio.file.NoSuchFileException e) {

				JOptionPane.showMessageDialog(cardPanel, I18nString.get("[Error] no such file."));
			} catch (java.io.IOException e) {

				JOptionPane.showMessageDialog(cardPanel, I18nString.get("[Error] incorrect p12file password."));
			} catch (Exception e) {

				JOptionPane.showMessageDialog(cardPanel, I18nString.get("[Error] failed to import."));
				errWithStackTrace(e);
			}
		});

		return panel;
	}

	public void showDialog() {
		setModal(true);
		setVisible(true);
	}
}
