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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import packetproxy.common.Utils;
import packetproxy.model.Packet;

public class GUIPacket
{
	private static GUIPacket instance;
	private JFrame owner;
	private GuiDataAll all_panel;
	private JTabbedPane packet_pane;
	private GUIData received_panel;
	private GUIData decoded_panel;
	private GUIData modified_panel;
	private GUIData sent_panel;
	private Packet showing_packet;

	//	public static void main(String args[])
	//	{
	//		try {
	//			GUIPacket gui = new GUIPacket();
	//			String s = "ABgNBHJfb2sAAAJhbANtc2cAB4NoAmEMYQANCg0KeyJlbXB0eSI6N30=";
	//			byte[] data = Base64.getDecoder().decode(s.getBytes());
	//			byte[] result = gui.prettyFormatJSONInRawData(data, "hoge");
	//			System.out.println(new String(result));
	//		} catch (Exception e) {
	//			e.printStackTrace();
	//		}
	//	}

	public static GUIPacket getInstance() throws Exception
	{
		if (instance == null) {
			instance = new GUIPacket();
		}
		return instance;
	}

	private GUIPacket() throws Exception {
		this.owner = GUIHistory.getOwner();
		this.showing_packet = null;
	}

	public JComponent createPanel() {
		received_panel = new GUIData(this.owner);
		decoded_panel = new GUIData(this.owner);
		modified_panel = new GUIData(this.owner);
		sent_panel = new GUIData(this.owner);
		all_panel = new GuiDataAll();

		packet_pane = new JTabbedPane();
		packet_pane.addTab("Received Packet", received_panel.createPanel());
		packet_pane.addTab("Decoded", decoded_panel.createPanel());
		packet_pane.addTab("Modified", modified_panel.createPanel());
		packet_pane.addTab("Encoded (Sent Packet)", sent_panel.createPanel());
		packet_pane.addTab("All", all_panel.createPanel());
		packet_pane.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				try {
					update();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		packet_pane.setSelectedIndex(1); /* decoded */
		return packet_pane;
	}

	public byte[] getData() {
		switch (packet_pane.getSelectedIndex()) {
			case 0:
				return received_panel.getData();
			case 1:
				return decoded_panel.getData();
			case 2:
				return modified_panel.getData();
			case 3:
				return sent_panel.getData();
			default:
				return modified_panel.getData();
		}
	}

	public void update() {
		if (showing_packet == null) {
			return;
		}
		switch (packet_pane.getSelectedIndex()) {
			case 0:
				received_panel.setData(showing_packet.getReceivedData()); break;
			case 1:
				decoded_panel.setData(showing_packet.getDecodedData()); 
				break;
			case 2:
				modified_panel.setData(showing_packet.getModifiedData()); break;
			case 3:
				sent_panel.setData(showing_packet.getSentData()); break;
			case 4:
				all_panel.setPacket(showing_packet); break;
			default:
		}
	}

	public void setPacket(Packet packet) {
		if (showing_packet != null && showing_packet.getId() == packet.getId()) {
			return;
		} else {
			showing_packet = packet;
		}
		update();
	}

	public Packet getPacket() {
		return showing_packet;
	}
}
