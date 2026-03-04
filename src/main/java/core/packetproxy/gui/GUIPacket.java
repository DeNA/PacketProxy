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

import javax.swing.JComponent;
import javax.swing.JFrame;
import packetproxy.model.Packet;

public class GUIPacket {

	private static GUIPacket instance;
	private JFrame owner;
	private GUIRequestResponsePanel request_response_panel;
	private Packet showing_packet;
	private Packet showing_response_packet;

	public static GUIPacket getInstance() throws Exception {
		if (instance == null) {

			instance = new GUIPacket();
		}
		return instance;
	}

	private GUIPacket() throws Exception {
		this.owner = GUIHistory.getOwner();
		this.showing_packet = null;
		this.showing_response_packet = null;
	}

	public JComponent createPanel() throws Exception {
		request_response_panel = new GUIRequestResponsePanel(this.owner);
		return request_response_panel.createPanel();
	}

	public byte[] getData() {
		return request_response_panel.getRequestData();
	}

	public void update() {
		if (showing_packet == null && showing_response_packet == null) {

			return;
		}
		request_response_panel.setRequestPacket(showing_packet);
		request_response_panel.setResponsePacket(showing_response_packet);
	}

	public void setPacket(Packet packet) {
		setSinglePacket(packet, false);
	}

	/**
	 * パケットを設定して表示を更新する
	 *
	 * @param packet
	 *            表示するパケット
	 * @param forceRefresh
	 *            trueの場合、同じパケットIDでも強制的に再描画する
	 */
	public void setPacket(Packet packet, boolean forceRefresh) {
		setSinglePacket(packet, forceRefresh);
	}

	public void setPackets(Packet requestPacket, Packet responsePacket) {
		setPackets(requestPacket, responsePacket, false);
	}

	public void setPackets(Packet requestPacket, Packet responsePacket, boolean forceRefresh) {
		if (!forceRefresh && isSameRequestResponse(requestPacket, responsePacket)) {
			return;
		}
		showing_packet = requestPacket;
		showing_response_packet = responsePacket;
		request_response_panel.setPackets(requestPacket, responsePacket);
	}

	public void setSinglePacket(Packet packet) {
		setSinglePacket(packet, false);
	}

	public void setSinglePacket(Packet packet, boolean forceRefresh) {
		if (!forceRefresh && isSameSinglePacket(packet)) {
			return;
		}
		showing_packet = packet;
		showing_response_packet = null;
		request_response_panel.setSinglePacket(packet);
	}

	public Packet getPacket() {
		return showing_packet;
	}

	public Packet getResponsePacket() {
		return showing_response_packet;
	}

	private boolean isSameSinglePacket(Packet packet) {
		return showing_packet != null && showing_response_packet == null && showing_packet.getId() == packet.getId();
	}

	private boolean isSameRequestResponse(Packet requestPacket, Packet responsePacket) {
		if (showing_packet == null || requestPacket == null) {
			return false;
		}
		if (showing_packet.getId() != requestPacket.getId()) {
			return false;
		}
		if (showing_response_packet == null || responsePacket == null) {
			return showing_response_packet == null && responsePacket == null;
		}
		return showing_response_packet.getId() == responsePacket.getId();
	}
}
