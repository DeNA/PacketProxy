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

import static packetproxy.model.PropertyChangeEventType.RESENDER_PACKETS;
import static packetproxy.model.PropertyChangeEventType.SELECTED_INDEX;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import packetproxy.controller.ResendController;
import packetproxy.controller.ResendController.ResendWorker;
import packetproxy.controller.SinglePacketAttackController;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.ResenderPacket;
import packetproxy.model.ResenderPackets;

public class GUIResender implements PropertyChangeListener {

	class ResendsCloseButtonTabbedPane extends CloseButtonTabbedPane {

		@Override
		public void removeTabAt(int index) {
			super.removeTabAt(index);
			try {

				int resends_index = resends_indexes.get(index);
				resends_indexes.remove(index);
				ResenderPackets.getInstance().deleteResends(resends_index);
			} catch (Exception e) {

				e.printStackTrace();
			}
		}
	}

	private JPanel main_panel;
	private ResendsCloseButtonTabbedPane resends_tabs; // 上側のタブ一覧
	private List<Integer> resends_indexes; // 上側のタブの番号一覧

	private static GUIResender instance;

	public static GUIResender getInstance() throws Exception {
		if (instance == null) {

			instance = new GUIResender();
		}
		return instance;
	}

	private GUIResender() throws Exception {
		main_panel = new JPanel();
		resends_tabs = new ResendsCloseButtonTabbedPane();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		main_panel.add(resends_tabs);
		resends_indexes = new ArrayList<Integer>();
		ResenderPackets.getInstance().addPropertyChangeListener(this);
		loadResenderPackets();
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!RESENDER_PACKETS.matches(evt)) {

			return;
		}

		main_panel.remove(resends_tabs);
		resends_tabs = new ResendsCloseButtonTabbedPane();
		main_panel.add(resends_tabs);
		resends_indexes.clear();
		loadResenderPackets();
	}

	private void loadResenderPackets() {
		try {

			List<ResenderPacket> resender_packets = ResenderPackets.getInstance().queryAllOrdered();
			int before_resends_index = -1;
			Resends resends = null;

			for (int i = 0; i < resender_packets.size(); i++) {

				ResenderPacket resender_packet = resender_packets.get(i);
				int resends_index = resender_packet.getResendsIndex();
				int resend_index = resender_packet.getResendIndex();

				if (resends_index != before_resends_index) {

					resends = new Resends();
					resends_tabs.addTab(String.valueOf(resends_index), resends.getComponent());
					resends_indexes.add(resends_index);
					before_resends_index = resends_index;
				}

				Resend resend = new Resend(resends);
				resends.resend_tabs.addTab(String.valueOf(resend_index), resend.getComponent());
				resends.resend_indexes.add(resend_index);

				if (resend_index == 1) {

					resend.setOneShotPacket(resender_packet.getOneShotPacket(), null);
				} else {

					ResenderPacket next_resender_packet = resender_packets.get(i + 1);
					if (resender_packet.getDirection() == Packet.Direction.CLIENT) {

						resend.setOneShotPacket(resender_packet.getOneShotPacket(),
								next_resender_packet.getOneShotPacket());
					} else {

						resend.setOneShotPacket(next_resender_packet.getOneShotPacket(),
								resender_packet.getOneShotPacket());
					}
					i++;
				}
			}
		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	public JComponent createPanel() {
		return main_panel;
	}

	public void addResends(OneShotPacket send_packet) throws Exception {
		Resends resends = new Resends();
		int resends_index = resends_indexes.isEmpty() ? 1 : resends_indexes.get(resends_indexes.size() - 1) + 1;
		resends_indexes.add(resends_index);
		resends_tabs.addTab(String.valueOf(resends_index), resends.getComponent());
		resends_tabs.setSelectedComponent(resends.getComponent());
		resends.addResend(send_packet, null);
	}

	class Resends {

		class ResendCloseButtonTabbedPane extends CloseButtonTabbedPane {

			@Override
			public void removeTabAt(int index) {
				super.removeTabAt(index);
				try {

					int resend_index = resend_indexes.get(index);
					resend_indexes.remove(index);
					int resends_index = resends_indexes.get(resends_tabs.getSelectedIndex());
					ResenderPackets.getInstance().deleteResend(resends_index, resend_index);
				} catch (Exception e) {

					e.printStackTrace();
				}
			}
		}

		private JPanel main_panel;
		private CloseButtonTabbedPane resend_tabs; // 下側のタブ一覧
		private List<Integer> resend_indexes; // 下側のタブの番号一覧

		public Resends() {
			resend_tabs = new ResendCloseButtonTabbedPane();
			main_panel = new JPanel();
			main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
			main_panel.add(resend_tabs);
			resend_indexes = new ArrayList<Integer>();
		}

		public void addResend(OneShotPacket send_packet, OneShotPacket recv_packet) throws Exception {
			Resend resend = new Resend(this);
			int resend_index = resend_indexes.isEmpty() ? 1 : resend_indexes.get(resend_indexes.size() - 1) + 1;
			resend_indexes.add(resend_index);
			resend_tabs.addTab(String.valueOf(resend_index), resend.getComponent());
			resend_tabs.setSelectedComponent(resend.getComponent());
			resend.setOneShotPacket(send_packet, recv_packet);

			int resends_index = resends_indexes.get(resends_tabs.getSelectedIndex());
			ResenderPackets.getInstance().createResend(send_packet.getResenderPacket(resends_index, resend_index));
			if (recv_packet != null) {

				ResenderPackets.getInstance().createResend(recv_packet.getResenderPacket(resends_index, resend_index));
			}
		}

		public JComponent getComponent() {
			return main_panel;
		}
	}

	class Resend implements PropertyChangeListener {

		private GUIServerNamePanel server_name_panel;
		private OneShotPacket send_saved;
		private OneShotPacket recv_saved;
		private GUIPacketData send_panel;
		private GUIPacketData recv_panel;
		private JSplitPane split_panel;
		private JButton resend_button;
		private JButton resend_multiple_button;
		private JButton attack_button;
		private JPanel main_panel;

		public Resend(Resends parent) throws Exception {
			server_name_panel = new GUIServerNamePanel();
			send_panel = new GUIPacketData();
			recv_panel = new GUIPacketData();

			send_panel.getTabs().addPropertyChangeListener(this);
			split_panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
			split_panel.setBackground(Color.WHITE);
			split_panel.add(send_panel.createPanel());
			split_panel.add(recv_panel.createPanel());
			split_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
			split_panel.setResizeWeight(0.5);
			resend_button = new JButton("send");
			resend_button.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					try {

						OneShotPacket sendPacket = send_panel.getOneShotPacket();
						ResendController.getInstance().resend(new ResendWorker(sendPacket, 1) {

							@Override
							protected void process(List<OneShotPacket> packets) {
								try {

									OneShotPacket recvPacket = packets.get(0);
									recv_panel.setOneShotPacket(recvPacket);
									parent.addResend(sendPacket, recvPacket);
									rollback();
								} catch (Exception e) {

									e.printStackTrace();
								}
							}
						});
					} catch (Exception e1) {

						e1.printStackTrace();
					}
				}
			});
			send_panel.setParentSend(resend_button);

			resend_multiple_button = new JButton("send x 20");
			resend_multiple_button.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					try {

						ResendController.getInstance().resend(send_panel.getOneShotPacket(), 20);
						clearLog();
						showLog("結果は履歴ウィンドウで確認してください！");
						rollback();
					} catch (Exception e1) {

						e1.printStackTrace();
					}
				}
			});

			attack_button = new JButton("send x 20 (single-packet attack)");
			attack_button.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						new SinglePacketAttackController(send_panel.getOneShotPacket()).attack(20);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			});

			JPanel button_panel = new JPanel();
			button_panel.add(resend_button);
			button_panel.add(resend_multiple_button);
			button_panel.add(attack_button);
			button_panel.setMaximumSize(new Dimension(Short.MAX_VALUE, 10));

			main_panel = new JPanel();
			main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
			main_panel.add(server_name_panel);
			main_panel.add(split_panel);
			main_panel.add(button_panel);
		}

		private void showLog(String log) throws Exception {
			log += "\n";
			recv_panel.appendData(log.getBytes());
		}

		private void clearLog() throws Exception {
			recv_panel.setData("".getBytes());
		}

		public JComponent getComponent() {
			return main_panel;
		}

		public void rollback() throws Exception {
			send_panel.setOneShotPacket(send_saved == null ? null : (OneShotPacket) send_saved.clone());
			recv_panel.setOneShotPacket(recv_saved == null ? null : (OneShotPacket) recv_saved.clone());
		}

		public void setOneShotPacket(OneShotPacket send_packet, OneShotPacket recv_packet) throws Exception {
			send_saved = send_packet == null ? null : (OneShotPacket) send_packet.clone();
			recv_saved = recv_packet == null ? null : (OneShotPacket) recv_packet.clone();
			send_panel.setOneShotPacket(send_packet);
			recv_panel.setOneShotPacket(recv_packet);
			server_name_panel.updateServerName(send_packet);
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (!(evt.getSource() instanceof TabSet) || !SELECTED_INDEX.matches(evt)) {

				return;
			}

			int selectedIndex = (int) evt.getNewValue();
			if (selectedIndex == 2) {

				resend_button.setEnabled(false);
				resend_multiple_button.setEnabled(false);
				attack_button.setEnabled(false);
			} else {

				resend_button.setEnabled(true);
				resend_multiple_button.setEnabled(true);
				attack_button.setEnabled(true);
			}
		}
	}
}
