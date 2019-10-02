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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.io.FileUtils;

import packetproxy.common.Utils;
import packetproxy.model.CAFactory;
import packetproxy.model.CAs.CA;

public class GUIOption
{
	private JFrame owner;

	public GUIOption(JFrame owner) {
		this.owner = owner;
	}
	private JComponent createTitle(String title) {
		JLabel label = new JLabel(title);
		label.setForeground(Color.ORANGE);
		label.setBackground(Color.WHITE);
		if (Utils.isWindows()) {
			label.setFont(new Font("Arial", Font.BOLD, 15));
		} else {
			label.setFont(new Font("Arial", Font.BOLD, 14));
		}
		int label_height = label.getMaximumSize().height;
		label.setMaximumSize(new Dimension(Short.MAX_VALUE, label_height));
		return label;
	}
	private JComponent createDescription(String description) {
		JLabel label = new JLabel(description);
		label.setForeground(Color.BLACK);
		label.setBackground(Color.WHITE);
		int label_height = label.getMaximumSize().height;
		label.setMaximumSize(new Dimension(Short.MAX_VALUE, label_height*2));
		label.setMinimumSize(new Dimension(Short.MAX_VALUE, label_height*2));
		return label;
	}
	private JComponent createElement(String title, String description) {
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(createTitle(title));
		panel.add(createDescription(description));
		panel.setAlignmentX(0.0f);
		return panel;
	}
	private JComponent createSeparator() {
		JSeparator line = new JSeparator();
		line.setMaximumSize(new Dimension(Short.MAX_VALUE, 5));
		return line;
	}
	public JComponent createPanel() throws Exception
	{
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createElement("Listen Ports","リスニングポートとパケットを転送するサーバを設定します"));
		GUIOptionListenPorts listenPorts = new GUIOptionListenPorts(owner);
		panel.add(listenPorts.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Servers","サーバと通信時に利用するエンコードを設定します"));
		GUIOptionServers servers = new GUIOptionServers(owner);
		panel.add(servers.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Auto Modifications","データの自動改ざんを実施します"));
		GUIOptionModifications mods = new GUIOptionModifications(owner);
		panel.add(mods.createPanel());

		panel.add(new JLabel("簡易16進数計算機"));
		panel.add(new GUIHexCalc().create());

		panel.add(createSeparator());

		panel.add(createElement("Intercept Rule","Intercept時にチェックするルールを設定します"));
		GUIOptionIntercepts intercepts = new GUIOptionIntercepts(owner);
		panel.add(intercepts.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Client Certificate","クライアント証明書を設定します"));
		GUIOptionClientCertificate clientCertificate = new GUIOptionClientCertificate(owner);
		panel.add(clientCertificate.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("SSL PassThrough","プロキシ利用(HTTP_PROXYタイプ利用)ポートにおいて、HTTPSパケットの解析をせずに素通しするHTTPSサーバを指定します。全てのプロキシポートは再起動されます"));
		GUIOptionSSLPassThrough ssl = new GUIOptionSSLPassThrough(owner);
		panel.add(ssl.createPanel());

		panel.add(createSeparator());

		panel.add(createElement("Private DNS server","サーバの名前を自分自身のIPアドレスに名前解決したいときに利用します"));
		GUIOptionPrivateDNS privateDNS = new GUIOptionPrivateDNS();
		panel.add(privateDNS.getPanel());

		panel.add(createSeparator());

		panel.add(createElement("PacketProxy CA証明書","PC/Mac/Linux/Android/iOSの信頼する証明書に登録してください。(拡張子は.crtが望ましいです)"));
		
		JPanel caPanel = new JPanel();
		caPanel.setBackground(Color.WHITE);
		caPanel.setLayout(new BoxLayout(caPanel, BoxLayout.X_AXIS));

		JComboBox<String> ca_combo = new JComboBox<String>();
		CAFactory.queryExportable().forEach(ca -> {
			ca_combo.addItem(ca.getUTF8Name());
			ca_combo.setEnabled(true);
		});
		ca_combo.setMaximumRowCount(CAFactory.queryExportable().size());
		ca_combo.setMaximumSize(new Dimension(500, 30));
		
		JButton b = new JButton("CA証明書の取得");
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					JFileChooser saveFile = new JFileChooser("PacketProxyCA.crt");
					saveFile.setAcceptAllFileFilterUsed(false);
					saveFile.addChoosableFileFilter(new FileNameExtensionFilter("証明書ファイル (.crt)", "crt"));
					saveFile.showSaveDialog(owner);
					File file = saveFile.getSelectedFile();
					if (file != null) {
						CA ca = CAFactory.findByUTF8Name((String)ca_combo.getSelectedItem()).get();
						byte[] derData = ca.getCACertificate();
						String derPath = file.getAbsolutePath() + ".crt";
						try (FileOutputStream fos = new FileOutputStream(derPath)) {
							fos.write(derData);
							fos.close();
							JOptionPane.showMessageDialog(owner, String.format("%sに保存しました！", derPath));
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		caPanel.add(ca_combo);
		caPanel.add(b);
		
		panel.add(caPanel);

		panel.setPreferredSize(new Dimension(1000, 1600));
		JScrollPane sc = new JScrollPane(panel);
		sc.getVerticalScrollBar().setUnitIncrement(16);
		return sc;
	}
}
