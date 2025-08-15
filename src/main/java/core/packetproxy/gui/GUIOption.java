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
import static packetproxy.util.Logging.err;
import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.model.CAFactory;
import packetproxy.model.CAs.CA;
import packetproxy.model.CAs.PacketProxyCAPerUser;
import packetproxy.model.InterceptOptions;

public class GUIOption {

	private JFrame owner;

	public GUIOption(JFrame owner) {
		this.owner = owner;
	}

	private JComponent createTitle(String title) throws Exception {
		JLabel label = new JLabel(title);
		label.setForeground(Color.decode("61136"));
		label.setBackground(Color.WHITE);
		label.setFont(FontManager.getInstance().getUICaptionFont());
		label.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMinimumSize().height));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JComponent createDescription(String description) throws Exception {
		JLabel label = new JLabel(description);
		label.setForeground(Color.BLACK);
		label.setBackground(Color.WHITE);
		label.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMinimumSize().height));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JComponent createElement(String title, String description) throws Exception {
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(createTitle(title));
		panel.add(createDescription(description));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private JComponent createSeparator() {
		JSeparator line = new JSeparator();
		line.setMaximumSize(new Dimension(Short.MAX_VALUE, line.getMinimumSize().height));
		return line;
	}

	public JComponent createPanel() throws Exception {
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createElement("Listen Ports",
				I18nString.get("Set listen port and server that packets are forwarded to.")));
		GUIOptionListenPorts listenPorts = new GUIOptionListenPorts(owner);
		panel.add(listenPorts.createPanel());

		panel.add(createSeparator());

		panel.add(
				createElement("Servers", I18nString.get("Set server and encode module to be used to encode packets.")));
		GUIOptionServers servers = new GUIOptionServers(owner);
		panel.add(servers.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Hostname Resolutions", I18nString.get("Set ip addr and server for DNS resolution.")));
		GUIOptionResolutions resolutions = new GUIOptionResolutions(owner);
		panel.add(resolutions.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Auto Modifications", I18nString.get("Set pattern for auto packet modification.")));
		GUIOptionModifications mods = new GUIOptionModifications(owner);
		panel.add(mods.createPanel());

		panel.add(new JLabel(I18nString.get("Hex calculator for binary pattern")));
		panel.add(new GUIHexCalc().create());

		panel.add(createSeparator());

		panel.add(createElement("Intercept Rules", ""));
		GUIOptionIntercepts intercepts = new GUIOptionIntercepts(owner);
		JComponent interceptPanel = intercepts.createPanel();

		JCheckBox interceptRule = new JCheckBox(I18nString.get("Use these intercept rules"));
		interceptRule.setSelected(InterceptOptions.getInstance().isEnabled());
		interceptRule.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent ae) {
				try {

					if (interceptRule.isSelected()) {

						InterceptOptions.getInstance().setEnabled(true);
					} else {

						InterceptOptions.getInstance().setEnabled(false);
					}
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});

		panel.add(interceptRule);
		panel.add(interceptPanel);

		panel.add(createSeparator());

		panel.add(
				createElement("Client Certificates", I18nString.get("Set client certificate to be used on SSL/TLS.")));
		GUIOptionClientCertificate clientCertificate = new GUIOptionClientCertificate(owner);
		panel.add(clientCertificate.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("SSL PassThrough", I18nString.get(
				"Set HTTPS server that packets are forwarded to without analyzing. These settings are enabled only if 'HTTP_PROXY' type is used.")));
		GUIOptionSSLPassThrough ssl = new GUIOptionSSLPassThrough(owner);
		panel.add(ssl.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Private DNS server",
				I18nString.get("Use private DNS server that resolves server name to the IP address of this pc.")));
		GUIOptionPrivateDNS privateDNS = new GUIOptionPrivateDNS();
		panel.add(privateDNS.getPanel());

		panel.add(createSeparator());

		panel.add(createElement("OpenVPN Server with Docker",
				I18nString.get("Use OpenVPN Server as Docker Container to proxy HTTP/HTTPS without DNS Spoofing.")));
		GUIOptionOpenVPN openVPN = new GUIOptionOpenVPN(owner);
		panel.add(openVPN.getPanel());

		panel.add(createSeparator());

		panel.add(createElement("Priority Order of HTTP Versions",
				I18nString.get("Set order of priority between HTTP1 and HTTP2.")));
		GUIOptionHttp http = new GUIOptionHttp();
		panel.add(http.createPanel());

		panel.add(createSeparator());

		panel.add(createTitle("PacketProxy CA Certificates & Private Keys"));

		JPanel caPanel = new JPanel();
		caPanel.setBackground(Color.WHITE);
		caPanel.setLayout(new BoxLayout(caPanel, BoxLayout.X_AXIS));

		JComboBox<String> ca_combo = new JComboBox<String>();
		CAFactory.queryExportable().forEach(ca -> {

			ca_combo.addItem(ca.getUTF8Name());
			ca_combo.setEnabled(true);
		});
		ca_combo.setMaximumRowCount(CAFactory.queryExportable().size());
		ca_combo.setMaximumSize(new Dimension(ca_combo.getPreferredSize().width, ca_combo.getMinimumSize().height));

		JButton exportCertButton = new JButton(I18nString.get("Export"));
		exportCertButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				CA ca = CAFactory.findByUTF8Name((String) ca_combo.getSelectedItem()).get();
				GUIOptionExportCertificateAndPrivateKeyDialog dlg = new GUIOptionExportCertificateAndPrivateKeyDialog(
						owner, ca);
				dlg.showDialog();
			}
		});

		JButton regenerateCertButton = new JButton(I18nString.get("Regenerate"));
		regenerateCertButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					String name = ca_combo.getSelectedItem().toString();
					CA ca = CAFactory.find(name).orElseThrow();
					int option = JOptionPane.showConfirmDialog(owner,
							String.format(I18nString.get("Regenerate %s?"), name),
							String.format(I18nString.get("Regenerate CA certificate"), name), JOptionPane.YES_NO_OPTION,
							JOptionPane.WARNING_MESSAGE);
					if (option == JOptionPane.YES_OPTION) {

						log("regenerate %s", name);
						ca.regenerateCA();
					}
				} catch (Exception exp) {

					err("RegenerateCertButton Action Error: %s", exp.getMessage());
				}
			}
		});

		JButton importCertButton = new JButton(I18nString.get("Import another certificate and private key"));
		importCertButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				PacketProxyCAPerUser ca = (PacketProxyCAPerUser) CAFactory.findByUTF8Name("PacketProxy per-user CA")
						.get();
				GUIOptionImportCertificateAndPrivateKeyDialog dlg = new GUIOptionImportCertificateAndPrivateKeyDialog(
						owner, ca);
				dlg.showDialog();
			}
		});

		caPanel.add(ca_combo);
		caPanel.add(exportCertButton);
		caPanel.add(regenerateCertButton);
		caPanel.add(importCertButton);
		caPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, caPanel.getMaximumSize().height));
		caPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		panel.add(caPanel);

		panel.add(createSeparator());

		panel.add(createElement("Character encodings",
				I18nString.get("Add/Remove character encodings to be used to display contents of packet.")));
		GUIOptionCharSets charsetsGUI = new GUIOptionCharSets(owner);
		panel.add(charsetsGUI.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Extensions", I18nString.get("Enable/Disable loaded extensions")));
		GUIOptionExtensions extensionsGUI = new GUIOptionExtensions(owner);
		panel.add(extensionsGUI.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Fonts", ""));
		GUIOptionFonts fontsGUI = new GUIOptionFonts(owner);
		panel.add(fontsGUI.createPanel());

		panel.add(createSeparator());
		panel.add(createElement("Import/Export configs (Experimental)", I18nString.get(
				"Import/Export configs by GET/POST http://localhost:32349/config with 'Authorization: [AccessToken]' header")));
		panel.add(new GUIOptionHubServer(owner).createPanel());

		panel.setMaximumSize(new Dimension(panel.getPreferredSize().width, panel.getMinimumSize().height));
		panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, panel.getMinimumSize().height));
		JScrollPane sc = new JScrollPane(panel);
		sc.getVerticalScrollBar().setUnitIncrement(16);
		return sc;
	}
}
