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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

import packetproxy.controller.InterceptController;
import packetproxy.model.InterceptModel;
import packetproxy.model.Packet;
import packetproxy.util.PacketProxyUtility;

public class GUIIntercept implements Observer
{
	private JFrame owner;
	private JButton forward_button;
	private JButton forward_multi_button;
	private JButton drop_button;
	private JToggleButton forward_enable;
	private InterceptModel interceptModel;
	private InterceptController intercept_controller;
	private GUIServerNamePanel server_name_panel;
	private TabSet tabs;
	private byte[] raw_original_data;
	private byte[] original_data;

	public GUIIntercept(JFrame owner) throws Exception {
		this.owner = owner;
		this.intercept_controller = InterceptController.getInstance();
		this.interceptModel = InterceptModel.getInstance();
		this.interceptModel.addObserver(this);
	}

	public JComponent createPanel() throws Exception {
		forward_enable = new JToggleButton("intercept is off");
		forward_enable.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				AbstractButton button = (AbstractButton) actionEvent.getSource();
				if (button.getModel().isSelected()) {
					intercept_controller.enableInterceptMode();
				} else {
					intercept_controller.disableInterceptMode(getInterceptData());
				}
			}
		});
		String cmd_key = "⌘";
		if (!PacketProxyUtility.getInstance().isMac()) {
			cmd_key="Ctrl+";
		}
		forward_button = new JButton("forward "+cmd_key+"F");
		forward_button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				try { intercept_controller.forward(getInterceptData()); } catch (Exception e1) { e1.printStackTrace(); }
			}
		});
		forward_multi_button = new JButton("forward x 20");
		forward_multi_button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try { intercept_controller.forward_multiple(getInterceptData()); } catch (Exception e1) { e1.printStackTrace(); }
			}
		});
		drop_button = new JButton("drop "+cmd_key+"D");
		drop_button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try { intercept_controller.drop(); } catch (Exception e1) { e1.printStackTrace(); }
			}
		});
		JPanel button_panel = new JPanel();
		button_panel.add(forward_enable);
		button_panel.add(forward_button);
		button_panel.add(forward_multi_button);
		button_panel.add(drop_button);
		button_panel.setMaximumSize(new Dimension(1000, 10));
		button_panel.setAlignmentX(0.5f);

		server_name_panel = new GUIServerNamePanel();

		tabs = new TabSet(true, false);

		JPanel panel = new JPanel();
		panel.add(button_panel);
		panel.add(server_name_panel);
		panel.add(tabs.getTabPanel());
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		int condition = JComponent.WHEN_IN_FOCUSED_WINDOW;
		
		int mask_key = KeyEvent.META_MASK;
		if (!PacketProxyUtility.getInstance().isMac()) {
			mask_key = KeyEvent.CTRL_MASK;
		}
		KeyStroke keyStroke_forward = KeyStroke.getKeyStroke(KeyEvent.VK_F, mask_key);
		InputMap inputMap_forward = forward_button.getInputMap(condition);
		ActionMap actionMap_forward = forward_button.getActionMap();
		inputMap_forward.put(keyStroke_forward, keyStroke_forward.toString());
		actionMap_forward.put(keyStroke_forward.toString(), new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				forward_button.doClick();
			}
		});

		KeyStroke keyStroke_drop = KeyStroke.getKeyStroke(KeyEvent.VK_D, mask_key);
		InputMap inputMap_drop = forward_button.getInputMap(condition);
		ActionMap actionMap_drop = forward_button.getActionMap();
		inputMap_drop.put(keyStroke_drop, keyStroke_drop.toString());
		actionMap_drop.put(keyStroke_drop.toString(), new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				drop_button.doClick();
			}
		});

		return panel;
	}
	private void setInterceptData(byte[] data, Packet client_packet, Packet server_packet) throws Exception {
		if (data == null)
			data = new byte[]{};
		server_name_panel.updateServerName(client_packet, server_packet);
		tabs.setData(data);
		raw_original_data = tabs.getRaw().getData();
		original_data = data;
	}
	private byte[] getInterceptData() {
		byte[] data = null;
		int index = tabs.getSelectedIndex();
		if (index == 0) { 
			if (Arrays.equals(raw_original_data, tabs.getRaw().getData())) {
				data = original_data;
			} else {
				data = tabs.getRaw().getData();
			}
		} else if (index == 1){
			data = tabs.getBinary().getData();
		} else if (index == 2) {
			data = tabs.getJson().getData();
		}
		return data;
	}
	@Override
	public void update(Observable arg0, Object arg1) {
		try {
			if (interceptModel.isInterceptEnabled() == true) {
				forward_enable.setText("intercept is on");
			} else {
				forward_enable.setText("intercept is off");
			}
			byte[] data = interceptModel.getData();
			Packet client_packet = interceptModel.getClientPacket();
			Packet server_packet = interceptModel.getServerPacket();
			setInterceptData(data, client_packet, server_packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
