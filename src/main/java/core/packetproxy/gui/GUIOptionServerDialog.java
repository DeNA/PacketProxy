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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import packetproxy.EncoderManager;
import packetproxy.model.Server;

public class GUIOptionServerDialog extends JDialog
{
	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton("キャンセル");
	private JButton button_set = new JButton("保存");
	private HintTextField text_ip = new HintTextField("(ex.) aaa.bbb.ccc.com or 1.2.3.4");
	private HintTextField text_port = new HintTextField("(ex.) 80");
	private HintTextField text_comment = new HintTextField("(ex.) game server for test");
	private JCheckBox checkbox_ssl = new JCheckBox("SSLを利用して接続が必要");
	private JCheckBox checkbox_dns = new JCheckBox("DNS偽装の対象とする");
	private JCheckBox checkbox_upstream_http_proxy = new JCheckBox("Upsteam Http Proxyとして定義する");
	JComboBox<String> combo = new JComboBox<String>();
	private int height = 500;
	private int width = 500;
	private Server server = null;

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
	public Server showDialog(Server preset)
	{
   		text_ip.setText(preset.getIp());
   		text_port.setText(Integer.toString(preset.getPort()));
   		combo.setSelectedItem(preset.getEncoder());
   		checkbox_ssl.setSelected(preset.getUseSSL());
   		checkbox_upstream_http_proxy.setSelected(preset.isHttpProxy());
   		checkbox_dns.setSelected(preset.isResolved());
   		text_comment.setText(preset.getComment());
		setModal(true);
		setVisible(true);
		if (server != null) {
			preset.setIp(text_ip.getText());
			preset.setPort(Integer.parseInt(text_port.getText()));
			preset.setEncoder(combo.getSelectedItem().toString());
			preset.setUseSSL(checkbox_ssl.isSelected());
			preset.setResolved(checkbox_dns.isSelected());
			preset.setHttpProxy(checkbox_upstream_http_proxy.isSelected());
			preset.setComment(text_comment.getText());
			return preset;
		}
		return server;
	}
	public Server showDialog()
	{
		EventQueue.invokeLater(new Runnable() {
			@Override public void run() {
				button_cancel.requestFocusInWindow();
			}
		});
		setModal(true);
		setVisible(true);
		return server;
	}

	private JComponent createModuleAlert() throws  Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(
				"<html>名前が重複したEncodeモジュールがあります。<br/>" +
				"重複したEncodeモジュールの名前は<br/>" +
				"{モジュール名}-{モジュールのJarファイル名}として扱われます。</html>"
		);
		label.setForeground(Color.red);
		label.setPreferredSize(new Dimension(150, label.getMaximumSize().height));
		panel.add(label);
		return panel;
	}

	private JComponent createModuleSetting() throws Exception {
		String[] names = EncoderManager.getInstance().getEncoderNameList();
		for (int i = 0; i < names.length; i++) {
			combo.addItem(names[i]);
		}
	    combo.setEnabled(true);
	    combo.setMaximumRowCount(names.length);
	    combo.setSelectedItem("HTTP");
	    return label_and_object("Encodeモジュール:", combo);
	}
	private JComponent createIpSetting() {
	    return label_and_object("サーバホスト:", text_ip);
	}
	private JComponent createPortSetting() {
	    return label_and_object("サーバポート:", text_port);
	}
	private JComponent createUseSSLSetting() {
	    return label_and_object("SSLの利用:", checkbox_ssl);
	}
	private JComponent createHttpProxySetting() {
	    return label_and_object("HTTPプロキシ:", checkbox_upstream_http_proxy);
	}
	private JComponent createDNSSetting() {
	    return label_and_object("DNS偽装:", checkbox_dns);
	}
	private JComponent createCommentSetting() {
	    return label_and_object("コメント:", text_comment);
	}
	public GUIOptionServerDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle("設定");
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - width/2, width, height); /* ド真ん中 */

		checkbox_upstream_http_proxy.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		if (checkbox_upstream_http_proxy.isSelected()) {
	    			combo.setSelectedItem("HTTP");
	    			combo.setEnabled(false);
	    			checkbox_ssl.setSelected(false);
	    			checkbox_ssl.setEnabled(false);
	    			checkbox_dns.setSelected(false);
	    			checkbox_dns.setEnabled(false);
	    		} else {
	    			combo.setEnabled(true);
	    			checkbox_ssl.setEnabled(true);
	    			checkbox_dns.setEnabled(true);
	    		}
	    	}
	    });

		Container c = getContentPane();
		JPanel panel = new JPanel(); 
	    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
	    
	    panel.add(createIpSetting());
	    panel.add(createPortSetting());
	    panel.add(createUseSSLSetting());
	    if(EncoderManager.getInstance().hasDuplicateModules()){
	        panel.add(createModuleAlert());
	    }
	    panel.add(createModuleSetting());
	    panel.add(createDNSSetting());
	    panel.add(createHttpProxySetting());
	    panel.add(createCommentSetting());
	    
	    panel.add(buttons());

		c.add(panel);

	    button_cancel.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		server = null;
	    		dispose();
	    	}
	    });

	    button_set.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		server = new Server(text_ip.getText(),
	    				Integer.parseInt(text_port.getText()),
	    				checkbox_ssl.isSelected(),
	    				combo.getSelectedItem().toString(),
	    				checkbox_dns.isSelected(),
	    				checkbox_upstream_http_proxy.isSelected(),
	    				text_comment.getText());
	    		dispose();
	    	}
	    });
	}
}
