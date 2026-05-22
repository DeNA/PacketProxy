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

import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import packetproxy.http.Http;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.PacketInfo;

public class GUIServerNamePanel extends JPanel {

	private JLabel client_label;
	private JLabel server_label;

	public GUIServerNamePanel() {
		setPreferredSize(new Dimension(100, 24));
		setMinimumSize(new Dimension(10, 24));
		setMaximumSize(new Dimension(1000, 24));
		SpringLayout layout = new SpringLayout();
		setLayout(layout);

		client_label = new JLabel(" ");
		client_label.setHorizontalAlignment(JLabel.LEFT);
		client_label.setVerticalAlignment(JLabel.TOP);
		layout.putConstraint(SpringLayout.WEST, client_label, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, client_label, 4, SpringLayout.NORTH, this);
		add(client_label);

		server_label = new JLabel(" ");
		server_label.setHorizontalAlignment(JLabel.LEFT);
		server_label.setVerticalAlignment(JLabel.TOP);
		layout.putConstraint(SpringLayout.WEST, server_label, 0, SpringLayout.EAST, client_label);
		layout.putConstraint(SpringLayout.NORTH, server_label, 4, SpringLayout.NORTH, this);
		add(server_label);
	}

	public void updateServerName(OneShotPacket packet) {
		byte[] data = packet == null ? null : packet.getData();
		updateServerName(data, packet);
	}

	public void updateServerName(Packet client_packet, Packet server_packet) {
		PacketInfo target_packet = server_packet == null ? client_packet : server_packet;
		byte[] client_decoded_data = client_packet == null ? null : client_packet.getModifiedData();
		updateServerName(client_decoded_data, target_packet);
	}

	private void updateServerName(byte[] client_data, PacketInfo packet) {
		try {

			if (packet != null) {

				String dir_str = packet.getDirection() == Packet.Direction.CLIENT ? " -> " : " <- ";
				client_label.setText(dir_str);
				if (client_data != null && Http.isHTTP(client_data)) {

					Http http = Http.create(client_data);
					String url = http.getURL(packet.getServerPort(), packet.getUseSSL());
					server_label.setText(String.format("%s (%s)", url, packet.getEncoder()));
				} else {

					server_label.setText(String.format("%s:%d (%s)", packet.getServerIP(), packet.getServerPort(),
							packet.getEncoder()));
				}
			} else {

				clearText();
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	private void clearText() {
		client_label.setText(" ");
		server_label.setText(" ");
	}
}
