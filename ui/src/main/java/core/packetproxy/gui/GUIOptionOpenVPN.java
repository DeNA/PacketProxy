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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import packetproxy.OpenVPN;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.model.ConfigBoolean;
import packetproxy.model.OpenVPNForwardPort;
import packetproxy.model.OpenVPNForwardPorts;

public class GUIOptionOpenVPN extends GUIOptionComponentBase<OpenVPNForwardPort> {

	private GUIOptionOpenVPNDialog dig;
	private JCheckBox checkBox;
	private JComboBox<String> vpnProtocol;
	private JTextField textField;
	private JRadioButton auto, manual;
	OpenVPNForwardPorts openVPNForwardPorts;
	private List<OpenVPNForwardPort> table_ext_list;

	private JPanel base;

	private OpenVPN openVPN;

	public GUIOptionOpenVPN(JFrame owner) throws Exception {
		super(owner);
		this.openVPN = OpenVPN.getInstance();
		this.openVPNForwardPorts = OpenVPNForwardPorts.getInstance();
		this.openVPNForwardPorts.addPropertyChangeListener(this);
		this.table_ext_list = new ArrayList<OpenVPNForwardPort>();

		String[] menu = {"Proto", "src port", "dst port"};
		int[] menuWidth = {80, 80, 80};
		MouseAdapter tableAction = new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					int columnIndex = table.columnAtPoint(e.getPoint());
					int rowIndex = table.rowAtPoint(e.getPoint());
					table.setRowSelectionInterval(rowIndex, columnIndex);
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener addAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					dig = new GUIOptionOpenVPNDialog(owner);
					OpenVPNForwardPort forwardPort = dig.showDialog();
					if (forwardPort != null) {

						OpenVPNForwardPorts.getInstance().create(forwardPort);
					}
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener editAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					OpenVPNForwardPort forwardPort = getSelectedTableContent();
					dig = new GUIOptionOpenVPNDialog(owner);
					OpenVPNForwardPort newPort = dig.showDialog(forwardPort);
					if (newPort != null) {

						OpenVPNForwardPorts.getInstance().delete(forwardPort);
						OpenVPNForwardPorts.getInstance().create(newPort);
					}
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		ActionListener removeAction = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					OpenVPNForwardPorts.getInstance().delete(getSelectedTableContent());
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		};
		jcomponent = createComponent(menu, menuWidth, tableAction, addAction, editAction, removeAction);
		updateImpl();

		checkBox = createCheckBox();
		textField = createAddressField();
		base = createPanel(checkBox, textField);
		updateState();
	}

	@Override
	protected void addTableContent(OpenVPNForwardPort forwardPort) {
		table_ext_list.add(forwardPort);
		try {

			option_model.addRow(new Object[]{forwardPort.getType().toString(), forwardPort.getFromPort(),
					forwardPort.getToPort(),});
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	protected void updateTable(List<OpenVPNForwardPort> forwardPorts) {
		clearTableContents();
		for (OpenVPNForwardPort forwardPort : forwardPorts) {

			addTableContent(forwardPort);
		}
	}

	@Override
	protected void updateImpl() {
		try {

			updateTable(openVPNForwardPorts.queryAll());
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	protected void clearTableContents() {
		option_model.setRowCount(0);
		table_ext_list.clear();
	}

	@Override
	protected OpenVPNForwardPort getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	@Override
	protected OpenVPNForwardPort getTableContent(int rowIndex) {
		return table_ext_list.get(rowIndex);
	}

	public JPanel getPanel() {
		return base;
	}

	private JPanel createPanel(JCheckBox checkBox, JTextField text) throws Exception {
		auto = new JRadioButton(
				I18nString.get("Auto (Replace resolved IP with local IP of suitable NIC automatically)"), true);
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
		rewriteRule.setMaximumSize(
				new Dimension(rewriteRule.getPreferredSize().width, rewriteRule.getMinimumSize().height));

		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(checkBox);
		panel.add(createProtoSetting());
		panel.add(rewriteRule);
		panel.add(createPanel());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private void radioButtonChangeHandler(ActionEvent e) {
		textField.setEnabled(manual.isSelected());
	}

	public boolean isAutoSpoofing() {
		return auto.isSelected();
	}

	public String getSpoofingIP() {
		if (auto.isSelected()) {

			try {

				return getLocalIP();
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		} else {

			return textField.getText();
		}
		return "";
	}

	private JCheckBox createCheckBox() {
		checkBox = new JCheckBox(I18nString.get("Use OpenVPN"));
		checkBox.addActionListener(e -> {
			if (checkBox.isSelected()) {

				String proto = vpnProtocol.getSelectedItem().toString();
				openVPN.startServer(this.getSpoofingIP(), proto);
			} else
				openVPN.stopServer();
		});
		checkBox.setMinimumSize(new Dimension(Short.MAX_VALUE, checkBox.getMaximumSize().height));
		return checkBox;
	}

	private JComponent createProtoSetting() {
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		vpnProtocol = new JComboBox<>();
		vpnProtocol.setPrototypeDisplayValue("xxxxxxx");
		vpnProtocol.addItem("TCP");
		vpnProtocol.addItem("UDP");
		vpnProtocol.setMaximumRowCount(vpnProtocol.getItemCount());
		vpnProtocol.setSelectedItem("UDP");
		vpnProtocol
				.setMaximumSize(new Dimension(vpnProtocol.getMinimumSize().width, vpnProtocol.getMinimumSize().height));
		panel.add(vpnProtocol);
		panel.add(new JLabel(I18nString.get("will be used for VPN")));
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, panel.getMaximumSize().height));
		return panel;
	}

	private JTextField createAddressField() {
		JTextField text = new JTextField("");
		try {

			text.setText(getLocalIP());
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		text.setMaximumSize(new Dimension(300, 30));
		text.setEnabled(false);
		return text;
	}

	public void updateState() {
		try {

			checkBox.setSelected(new ConfigBoolean("OpenVPN").getState());
			if (checkBox.isSelected()) {

				String proto = this.vpnProtocol.getSelectedItem().toString();
				openVPN.startServer(this.getSpoofingIP(), proto);
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	private String getLocalIP() throws Exception {
		Enumeration<NetworkInterface> enuIfs = NetworkInterface.getNetworkInterfaces();
		List<String> ips = new ArrayList<>();
		String pubIp = null, corpIp = null;

		while (enuIfs.hasMoreElements()) {

			NetworkInterface ni = (NetworkInterface) enuIfs.nextElement();
			Enumeration<InetAddress> enuAddrs = ni.getInetAddresses();
			while (enuAddrs.hasMoreElements()) {

				InetAddress in4 = (InetAddress) enuAddrs.nextElement();
				String ip = in4.getHostAddress();
				if (ip.contains(":"))
					continue;
				ips.add(ip);
			}
		}

		for (String ip : ips) {

			if (ip.startsWith("172.23"))
				corpIp = ip;
			if (ip.startsWith("172.25"))
				pubIp = ip;
		}
		if (pubIp != null)
			return pubIp;
		if (corpIp != null)
			return corpIp;
		if (!ips.isEmpty()) {

			for (String ip : ips) {

				if (!ip.equals("127.0.0.1")) {

					return ip;
				}
			}
		}
		return "127.0.0.1";
	}
}
