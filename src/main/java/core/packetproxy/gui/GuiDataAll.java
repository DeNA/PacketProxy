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

import java.awt.GridLayout;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import packetproxy.common.I18nString;
import packetproxy.model.Packet;

public class GUIDataAll
{
	private JComponent main_panel;
	private RawTextPane received_text;
	private RawTextPane decoded_text;
	private RawTextPane modified_text;
	private RawTextPane sent_text;

	private RawTextPane createTextPane(String label_name) throws Exception {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JLabel label = new JLabel(label_name);
		label.setAlignmentX(0.5f);

		RawTextPane text = new RawTextPane();
		text.setEditable(false);
		panel.add(label);
		JScrollPane scroll = new JScrollPane(text);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		panel.add(scroll);
		main_panel.add(panel);
		return text;
	}

	public GUIDataAll() throws Exception {
		main_panel = new JPanel();
		main_panel.setLayout(new GridLayout(1,4));
		received_text = createTextPane(I18nString.get("Received"));
		decoded_text = createTextPane(I18nString.get("Decoded"));
		modified_text = createTextPane(I18nString.get("Modified"));
		sent_text = createTextPane(I18nString.get("Encoded"));
	}

	public JComponent createPanel() {
		return main_panel;
	}

	public void setPacket(Packet packet) {
		try {
			received_text.setData(packet.getReceivedData(), true);
			received_text.setCaretPosition(0);
			decoded_text.setData(packet.getDecodedData(), true);
			decoded_text.setCaretPosition(0);
			modified_text.setData(packet.getModifiedData(), true);
			modified_text.setCaretPosition(0);
			sent_text.setData(packet.getSentData(), true);
			sent_text.setCaretPosition(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
