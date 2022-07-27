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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import packetproxy.controller.ResendController;
import packetproxy.controller.ResendController.ResendWorker;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.model.RegexParam;

public class GUIBulkSender {
	private static GUIBulkSender instance;
	private static JFrame owner;

	public static JFrame getOwner() {
		return owner;
	}

	public static GUIBulkSender getInstance() throws Exception {
		if (instance == null) {
			instance = new GUIBulkSender();
		}
		return instance;
	}

	private Map<Integer, OneShotPacket> sendPackets;
	private Map<Integer, Integer> sendPacketIds;
	private Map<Integer, OneShotPacket> recvPackets;

	private GUIBulkSender() throws Exception {
		sendPackets = new HashMap<Integer, OneShotPacket>();
		sendPacketIds = new HashMap<Integer, Integer>();
		recvPackets = new HashMap<Integer, OneShotPacket>();
		sendPacketId = 0;
	}

	public JComponent createPanel() throws Exception {
		JSplitPane vsplit_panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		vsplit_panel.add(createSendPanel());
		vsplit_panel.add(createRecvPanel());
		return vsplit_panel;
	}

	public void add(OneShotPacket oneshot, int packetId) throws Exception {
		oneshot.setId(sendPacketId);
		sendPackets.put(sendPacketId, oneshot);
		sendPacketIds.put(sendPacketId, packetId);
		sendTable.add(oneshot);
		sendPacketId++;
	}

	private GUIBulkSenderTable sendTable;
	private GUIBulkSenderTable recvTable;
	private GUIBulkSenderData sendData;
	private GUIBulkSenderData recvData;
	private int selectedSendPacketId;
	private int selectedRecvPacketId;
	private int sendPacketId;

	private JComponent createSendPanel() throws Exception {
		sendData = new GUIBulkSenderData(owner, GUIBulkSenderData.Type.CLIENT, data -> {
			OneShotPacket pkt = sendPackets.get(selectedSendPacketId);
			if (pkt != null)
				pkt.setData(data);
		});
		sendTable = new GUIBulkSenderTable(GUIBulkSenderTable.Type.CLIENT, oneshotId -> {
			selectedSendPacketId = oneshotId;
			OneShotPacket pkt = sendPackets.get(oneshotId);
			if (pkt != null)
				sendData.setData(pkt.getData());
		});

		JButton sendButton = new JButton("Send all packets");
		sendButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					List<RegexParam> regexParams = sendTable.getRegexParams();
					recvTable.clear();
					recvPackets.clear();
					OneShotPacket[] oneshots = (OneShotPacket[]) sendPackets.values().toArray(new OneShotPacket[0]);

					if (regexParams.size() == 0) { // parallel
						ResendController.getInstance().resend(new ResendWorker(oneshots) {
							@Override
							protected void process(List<OneShotPacket> oneshots) {
								try {
									for (OneShotPacket oneshot : oneshots) {
										recvPackets.put(oneshot.getId(), oneshot);
										recvTable.add(oneshot);
										Packet packet = Packets.getInstance().query(sendPacketIds.get(oneshot.getId()));
										packet.setResend();
										Packets.getInstance().update(packet);
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						});
					} else { // sequential
						new Thread() {
							public void run() {
								try {
									for (int i = 0; i < oneshots.length; i++) {
										int idx = i;
										OneShotPacket oneshot = oneshots[i];
										CountDownLatch latch = new CountDownLatch(1);
										// modify packet
										for (RegexParam regexParam : regexParams) {
											if (regexParam.getValue() != "") {
												oneshot = regexParam.applyToPacket(oneshot);
											}
										}
										final OneShotPacket sendOneshot = oneshot;
										ResendController.getInstance().resend(new ResendWorker(sendOneshot, 1) {
											@Override
											protected void process(List<OneShotPacket> oneshots) {
												try {
													for (OneShotPacket oneshot : oneshots) {
														recvPackets.put(oneshot.getId(), oneshot);
														recvTable.add(oneshot);
														Packet packet = Packets.getInstance()
																.query(sendPacketIds.get(oneshot.getId()));
														packet.setResend();
														Packets.getInstance().update(packet);
														// pickup regex value
														regexParams.stream().filter(v -> {
															return v.getPacketId() == idx;
														}).forEach(v -> {
															regexParams.get(regexParams.indexOf(v))
																	.setValue(oneshot);
														});
														latch.countDown();
													}
												} catch (Exception e) {
													e.printStackTrace();
												}
											}
										});
										latch.await();
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}.start();
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		JButton clearButton = new JButton("clear");
		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					sendTable.clear();
					recvTable.clear();
					sendPackets.clear();
					sendPacketIds.clear();
					recvPackets.clear();
					sendData.setData("".getBytes());
					recvData.setData("".getBytes());
					sendPacketId = 0;
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(sendButton);
		buttonPanel.add(clearButton);
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

		JPanel bottomHalf = new JPanel();
		bottomHalf.add(sendData.createPanel());
		bottomHalf.add(buttonPanel);
		bottomHalf.setLayout(new BoxLayout(bottomHalf, BoxLayout.Y_AXIS));

		JSplitPane split_panel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split_panel.add(sendTable.createPanel());
		split_panel.add(bottomHalf);
		split_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
		split_panel.setDividerLocation(0.5);
		return split_panel;
	}

	private JComponent createRecvPanel() throws Exception {
		recvData = new GUIBulkSenderData(owner, GUIBulkSenderData.Type.SERVER, data -> {
			OneShotPacket pkt = recvPackets.get(selectedRecvPacketId);
			if (pkt != null)
				pkt.setData(data);
		});
		recvTable = new GUIBulkSenderTable(GUIBulkSenderTable.Type.SERVER, oneshotId -> {
			selectedRecvPacketId = oneshotId;
			OneShotPacket pkt = recvPackets.get(oneshotId);
			if (pkt != null)
				recvData.setData(pkt.getData());
		});
		JSplitPane split_panel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split_panel.add(recvTable.createPanel());
		split_panel.add(recvData.createPanel());
		split_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
		return split_panel;
	}

}
