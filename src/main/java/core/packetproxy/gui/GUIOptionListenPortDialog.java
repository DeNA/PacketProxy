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
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import packetproxy.common.I18nString;
import packetproxy.model.CAFactory;
import packetproxy.model.ListenPort;
import packetproxy.model.Server;
import packetproxy.model.Servers;
import packetproxy.util.PacketProxyUtility;

public class GUIOptionListenPortDialog extends JDialog
{
	private static final long serialVersionUID = 1L;
	private JPanel main_panel = new JPanel(); 
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private JTextField text_port = new JTextField();
	JComboBox<String> combo = new JComboBox<String>();
	JComboBox<String> type_combo = new JComboBox<String>();
	JComboBox<String> ca_combo = new JComboBox<String>();
	private int height = 400;
	private int width = 600;
	private ListenPort listenPort = null;
	private String last_server_str = null; // Typeを変更した時に設定を引き継ぐ用

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
	public ListenPort showDialog(ListenPort preset) throws Exception
	{
		text_port.setText(Integer.toString(preset.getPort()));
		type_combo.setSelectedItem(preset.getType().toString());
		if (preset.getServer() != null)
			combo.setSelectedItem(preset.getServer().toString());
		ca_combo.setSelectedItem(preset.getCA().get().getUTF8Name());
		setModal(true);
		setVisible(true);
		return listenPort;
	}
	public ListenPort showDialog()
	{
		setModal(true);
		setVisible(true);
		return listenPort;
	}
	private JComponent createForwardedServers() throws Exception {
		combo.setEnabled(true);
		updateNextHopList("HTTP_PROXY");
		combo.addItemListener(new ItemListener(){
			@Override
			public void itemStateChanged(ItemEvent event) {
				try {
					if (event.getStateChange() != ItemEvent.SELECTED ||
							type_combo.getSelectedItem() == null ||
							type_combo.getSelectedItem().toString().equals("HTTP_PROXY") ||
							type_combo.getSelectedItem().toString().equals("SSL_TRANSPARENT_PROXY") ||
							type_combo.getSelectedItem().toString().equals("HTTP_TRANSPARENT_PROXY")) 
						return;
					Object server_str = combo.getSelectedItem();
					if (server_str != null) {
						last_server_str = server_str.toString();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		return label_and_object(I18nString.get("Forward to:"), combo);
	}

	private void updateNextHopList(String item) throws Exception {
		String server_str = last_server_str;
		combo.removeAllItems();
		List<Server> servers = null;
		if (item.equals("HTTP_PROXY")) {
			servers = Servers.getInstance().queryHttpProxies();
			combo.addItem(I18nString.get("Forward to server directly without upsteam proxy"));
		} else if (item.equals("SSL_TRANSPARENT_PROXY")) {
			servers = new ArrayList<Server>();
			combo.addItem(I18nString.get("Forward to server specified in SNI header"));
		} else if (item.equals("HTTP_TRANSPARENT_PROXY")) {
			servers = new ArrayList<Server>();
			combo.addItem(I18nString.get("Forward to server specified in Hosts header"));
		} else if (item.equals("UDP_FORWARDER")) {
			servers = Servers.getInstance().queryNonHttpProxies();
			if (servers.isEmpty()) {
				JOptionPane.showMessageDialog(this, I18nString.get("Set server you wish to connect in 'Servers' setting first."));
				dispose();
			}
		} else if (item.equals("SSL_FORWARDER")) {
			servers = Servers.getInstance().queryNonHttpProxies();
			if (servers.isEmpty()) {
				JOptionPane.showMessageDialog(this, I18nString.get("Set server you wish to connect in 'Servers' setting first."));
				dispose();
			}
		} else if (item.equals("FORWARDER")) {
			servers = Servers.getInstance().queryNonHttpProxies();
			if (servers.isEmpty()) {
				JOptionPane.showMessageDialog(this, I18nString.get("Set server you wish to connect in 'Servers' setting first."));
				dispose();
			}
		} else {
			servers = Servers.getInstance().queryNonHttpProxies();
		}
		for (Server server : servers) {
			combo.addItem(server.toString());
		}
		if (server_str != null) {
			combo.setSelectedItem(server_str.toString());
		}
		combo.setMaximumRowCount(combo.getItemCount());
	}

	private JComponent createTypeSetting() {
		type_combo.addItem("HTTP_PROXY");
		type_combo.addItem("FORWARDER");
		type_combo.addItem("SSL_FORWARDER");
		type_combo.addItem("SSL_TRANSPARENT_PROXY");
		type_combo.addItem("HTTP_TRANSPARENT_PROXY");
		type_combo.addItem("UDP_FORWARDER");
		type_combo.setEnabled(true);
		type_combo.setMaximumRowCount(6);
		type_combo.addItemListener(new ItemListener(){
			@Override
			public void itemStateChanged(ItemEvent event) {
				try {
					if (event.getStateChange() != ItemEvent.SELECTED)
						return;
					updateNextHopList((String)event.getItem());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		return label_and_object("Type:", type_combo);
	}
	private JComponent createPortSetting() {
		return label_and_object("Listen Port:", text_port);
	}
	private JComponent createCASetting() {
		CAFactory.queryAll().stream().forEach(ca -> ca_combo.addItem(ca.getUTF8Name()));
		ca_combo.setEnabled(true);
		ca_combo.setMaximumRowCount(CAFactory.queryAll().size());
		ca_combo.setSelectedItem("PacketProxy per-user CA");
		return label_and_object(I18nString.get("CA certificate to sign:"), ca_combo);
	}
	public GUIOptionListenPortDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle(I18nString.get("Listenning Port Setting"));
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - height/2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));

		main_panel.add(createPortSetting());
		main_panel.add(createTypeSetting());
		main_panel.add(createForwardedServers());
		main_panel.add(createCASetting());

		main_panel.add(buttons());

		c.add(main_panel);

		button_cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				listenPort = null;
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					ListenPort.TYPE type = null; 
					if (type_combo.getSelectedItem().toString().equals("HTTP_PROXY")) {
						type = ListenPort.TYPE.HTTP_PROXY;
					} else if (type_combo.getSelectedItem().toString().equals("FORWARDER")) {
						type = ListenPort.TYPE.FORWARDER;
					} else if (type_combo.getSelectedItem().toString().equals("UDP_FORWARDER")) {
						type = ListenPort.TYPE.UDP_FORWARDER;
					} else if (type_combo.getSelectedItem().toString().equals("SSL_TRANSPARENT_PROXY")) {
						PacketProxyUtility.getInstance().packetProxyLog("SSL_TRANSPARENT_PROXY created");
						type = ListenPort.TYPE.SSL_TRANSPARENT_PROXY;
					} else if (type_combo.getSelectedItem().toString().equals("HTTP_TRANSPARENT_PROXY")) {
						PacketProxyUtility.getInstance().packetProxyLog("HTTP_TRANSPARENT_PROXY created");
						type = ListenPort.TYPE.HTTP_TRANSPARENT_PROXY;
					} else {
						type = ListenPort.TYPE.SSL_FORWARDER;
					}
					String server_str = (String)combo.getSelectedItem();
					listenPort = new ListenPort( Integer.parseInt(text_port.getText()),
							type,
							Servers.getInstance().queryByString(server_str),
							CAFactory.findByUTF8Name(ca_combo.getSelectedItem().toString()).map(ca -> ca.getName()).orElse("Error")
							);
					dispose();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
	}
}
