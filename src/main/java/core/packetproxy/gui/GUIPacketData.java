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
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import packetproxy.model.OneShotPacket;

public class GUIPacketData {

	private JPanel main_panel;
	private TabSet tabs;
	private OneShotPacket showing_packet;

	public GUIPacketData() throws Exception {
		showing_packet = null;
		main_panel = new JPanel();
		tabs = new TabSet(true, false);
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		main_panel.add(tabs.getTabPanel());
		main_panel.setPreferredSize(new Dimension(200, 100));
	}

	public JComponent createPanel() {
		return main_panel;
	}

	public TabSet getTabs() {
		return tabs;
	}

	public void update() {
		try {

			byte[] data = (showing_packet == null) ? new byte[]{} : showing_packet.getData();
			tabs.setData(data);
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	public void setOneShotPacket(OneShotPacket oneshot) {
		showing_packet = oneshot;
		update();
	}

	public void clear() {
		showing_packet = null;
		update();
	}

	public OneShotPacket getOneShotPacket() {
		byte[] data = tabs.getData();
		if (data != null) {

			showing_packet.setData(data);
		}
		return showing_packet;
	}

	public void setData(byte[] data) throws Exception {
		tabs.getRaw().setData(data);
	}

	public void appendData(byte[] data) throws Exception {
		tabs.getRaw().appendData(data);
	}

	public void setParentSend(JButton parentSend) {
		tabs.setParentSend(parentSend);
	}
}
