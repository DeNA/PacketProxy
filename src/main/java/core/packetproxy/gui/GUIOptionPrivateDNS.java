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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import org.xbill.DNS.DNSSpoofingIPGetter;

import packetproxy.PrivateDNS;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
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

	private JPanel createPanel(JCheckBox checkBox, JTextField text) throws Exception {
		auto = new JRadioButton(I18nString.get("Auto (Replace resolved IP with local IP of suitable NIC automatically)"), true);
		auto.setMinimumSize(new Dimension(Short.MAX_VALUE, auto.getMaximumSize().height));
		auto.addActionListener(this::radioButtonChangeHandler);
		manual = new JRadioButton(I18nString.get("Manual"), false);
		manual.addActionListener(this::radioButtonChangeHandler);

		ButtonGroup rewriteGroup = new ButtonGroup();
		rewriteGroup.add(auto);
		rewriteGroup.add(manual);

		JPanel manualPanel = new JPanel();
		manualPanel.setBackground(Color.WHITE);
		manualPanel.setLayout(new BoxLayout(manualPanel, BoxLayout.X_AXIS));
		manualPanel.add(manual);
		manualPanel.add(text);

		TitledBorder rewriteRuleBorder = new TitledBorder(I18nString.get("Rewrite Rule"));
		LineBorder inborder = new LineBorder(Color.BLACK, 1);
		rewriteRuleBorder.setBorder(inborder);
		rewriteRuleBorder.setTitleFont(FontManager.getInstance().getUIFont());
		rewriteRuleBorder.setTitleJustification(TitledBorder.LEFT);
		rewriteRuleBorder.setTitlePosition(TitledBorder.TOP);

		JPanel rewriteRule = new JPanel();
		rewriteRule.setLayout(new BoxLayout(rewriteRule, BoxLayout.Y_AXIS));
		rewriteRule.setBackground(Color.WHITE);
		rewriteRule.setBorder(rewriteRuleBorder);
		rewriteRule.add(auto);
		rewriteRule.add(manualPanel);
		rewriteRule.setMaximumSize(new Dimension(rewriteRule.getPreferredSize().width, rewriteRule.getMinimumSize().height));

		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(checkBox);
		panel.add(rewriteRule);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		return panel;
	}

	private void radioButtonChangeHandler(ActionEvent e){
		textField.setEnabled(manual.isSelected());
	}
	
	public boolean isAutoSpoofing() {
		return auto.isSelected();
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
		checkBox = new JCheckBox(I18nString.get("Use private DNS server"));
		checkBox.addActionListener(e ->{
			if(checkBox.isSelected()) privateDNS.start(new DNSSpoofingIPGetter(this));
			else privateDNS.stop();
		});
		checkBox.setMinimumSize(new Dimension(Short.MAX_VALUE, checkBox.getMaximumSize().height));
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
