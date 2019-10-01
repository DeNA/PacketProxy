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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.xbill.DNS.DNSSpoofingIPGetter;

import packetproxy.PrivateDNS;
import packetproxy.model.ConfigBoolean;
import packetproxy.model.Configs;

public class GUIOptionPrivateDNS implements Observer
{
	private PrivateDNS privateDNS;

	private JCheckBox checkBox;
	private JTextField textField;
	private JRadioButton auto, manual;

	private JPanel base;

	public GUIOptionPrivateDNS() throws Exception {
		privateDNS = PrivateDNS.getInstance();
		
		checkBox = createCheckBox();
		textField = createAddressField();
		base = createPanel(checkBox, textField);

		Configs.getInstance().addObserver(this);
		updateState();
	}
	
	public  JPanel getPanel(){
		return base;
	}
	
	private JPanel createPanel(JCheckBox checkBox, JTextField text){
		JPanel panel = new JPanel();
		panel.setMaximumSize(new Dimension(900, 100));
		panel.setBackground(Color.WHITE);
		panel.setLayout(new GridLayout(4, 2));

		panel.add(checkBox);
		panel.add(new JLabel("", 0));

		panel.add(new JLabel("書き換え先IP"));
		panel.add(new JLabel("", 0));

		ButtonGroup group = new ButtonGroup();
		auto = new JRadioButton("自動", true);
		manual = new JRadioButton("手動", false);
		group.add(auto);group.add(manual);
		auto.addActionListener(this::radioButtonChangeHandler);
		manual.addActionListener(this::radioButtonChangeHandler);
		
		panel.add(auto);panel.add(new JLabel("適当なnicのローカルIPに置き換えます"));
		panel.add(manual); panel.add(text);

		return panel;
	}
	
	private void radioButtonChangeHandler(ActionEvent e){
		textField.setEnabled(manual.isSelected());
	}
	
	public String getSpoofingIP(){
		if(auto.isSelected()){
			try {
				return getLocalIP();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else{
			return textField.getText();
		}
		return "";
	}
	
	
	private JCheckBox createCheckBox(){
		checkBox = new JCheckBox("プライベートDNSサーバを起動する");
		checkBox.addActionListener(e ->{
			if(checkBox.isSelected()) privateDNS.start(new DNSSpoofingIPGetter(this));
			else privateDNS.stop();
		});
		return checkBox;
	}
	
	private JTextField createAddressField(){
		JTextField text = new JTextField("");
		try {
			text.setText(getLocalIP());
		} catch (Exception e) {
			e.printStackTrace();
		}
	    text.setMaximumSize(new Dimension(300, 30));
	    text.setEnabled(false);
		return text;
	}

	public void updateState(){
		try {
			checkBox.setSelected(new ConfigBoolean("PrivateDNS").getState());
			if(checkBox.isSelected()) privateDNS.start(new DNSSpoofingIPGetter(this));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void update(Observable o, Object arg) {
		updateState();
	}

	private String getLocalIP() throws Exception{
		Enumeration<NetworkInterface> enuIfs = NetworkInterface.getNetworkInterfaces();
		List<String> ips = new ArrayList<>();
		String pubIp = null, corpIp = null;

		while (enuIfs.hasMoreElements()){
			NetworkInterface ni = (NetworkInterface)enuIfs.nextElement();
			Enumeration<InetAddress> enuAddrs = ni.getInetAddresses();
			while (enuAddrs.hasMoreElements()) {
				InetAddress in4 = (InetAddress)enuAddrs.nextElement();
				String ip = in4.getHostAddress();
				if(ip.contains(":"))continue;
				ips.add(ip);
			}
		}

		for(String ip : ips){
			if(ip.startsWith("172.23"))corpIp = ip;
			if(ip.startsWith("172.25"))pubIp = ip;
		}
		if(pubIp != null)return pubIp;
		if(corpIp != null)return corpIp;
		if(!ips.isEmpty())return ips.get(0);
		return "127.0.0.1";
	}
}
