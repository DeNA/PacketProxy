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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import packetproxy.common.FontManager;
import packetproxy.common.Range;
import packetproxy.controller.ResendController;
import packetproxy.controller.ResendController.ResendWorker;
import packetproxy.model.OneShotPacket;
import packetproxy.vulchecker.VulCheckPattern;
import packetproxy.vulchecker.VulChecker;
import packetproxy.vulchecker.generator.Generator;

public class GUIVulCheckTab {
	private static JFrame owner;

	public static JFrame getOwner() {
		return owner;
	}

	private String name;
	private GUIVulCheckManager manager;
	private Map<Integer, OneShotPacket> recvPackets;

	public GUIVulCheckTab(VulChecker vulChecker, OneShotPacket packet, Range range) throws Exception {
		this.name = vulChecker.getName();
		manager = new GUIVulCheckManager(vulChecker, packet, range);
		recvPackets = new HashMap<Integer, OneShotPacket>();
		recvPacketId = 0;
	}

	public JComponent createPanel() throws Exception {
		JSplitPane vsplit_panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		vsplit_panel.add(createSendPanel());
		vsplit_panel.add(createRecvPanel());

		JLabel label = new JLabel(name);
		label.setBackground(Color.WHITE);
		label.setForeground(new Color(0, 200, 0));
		label.setFont(FontManager.getInstance().getUICaptionFont());

		JPanel main = new JPanel();
		main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
		main.setBackground(Color.WHITE);
		main.add(label);
		main.add(vsplit_panel);

		// initialize sendTable
		for (Generator generator : manager.getGenerators()) {
			sendTable.add(generator.getName(), manager.findVulCheckPattern(generator.getName()).getPacket(),
					manager.isEnabled(generator.getName()));
		}

		return main;
	}

	private GUIVulCheckSendTable sendTable;
	private GUIVulCheckRecvTable recvTable;
	private TabSet sendData;
	private TabSet recvData;
	private String selectedGeneratorName = "";
	private int selectedRecvPacketId;
	private int sendPacketId;
	private int recvPacketId;

	private JComponent createSendPanel() throws Exception {
		sendData = new TabSet(true, false);
		sendTable = new GUIVulCheckSendTable(generatorName -> { // onSelected
			try {
				if (!selectedGeneratorName.isEmpty() && !selectedGeneratorName.equals(generatorName)) {
					VulCheckPattern vulCheckPattern = manager.findVulCheckPattern(selectedGeneratorName);
					OneShotPacket packet = vulCheckPattern.getPacket();
					if (Arrays.compare(packet.getData(), sendData.getData()) != 0) {
						packet.setData(sendData.getData());
						manager.saveVulCheckPattern(selectedGeneratorName,
								new VulCheckPattern(vulCheckPattern.getName(), packet, null));
					}
				}
				selectedGeneratorName = generatorName;
				VulCheckPattern v = manager.findVulCheckPattern(generatorName);
				if (v != null) {
					sendData.setData(v.getPacket().getData(), v.getRange());
				}
			} catch (Exception e) {
			}
		}, generatorName -> { // onEnabled
			try {
				manager.setEnabled(generatorName, true);
				VulCheckPattern v = manager.findVulCheckPattern(generatorName);
				if (v != null) {
					sendData.setData(v.getPacket().getData(), v.getRange());
					sendTable.setRow(generatorName, v.getPacket());
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		}, generatorName -> { // onDisabled
			try {
				manager.setEnabled(generatorName, false);
				VulCheckPattern v = manager.findVulCheckPattern(generatorName);
				if (v != null) {
					sendData.setData(v.getPacket().getData(), v.getRange());
					sendTable.setRow(generatorName, v.getPacket());
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		});

		JButton sendButton = new JButton("send");
		sendButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					String generatorName = sendTable.getSelectedGeneratorName();
					if (!manager.isEnabled(generatorName)) {
						return;
					}
					VulCheckPattern pattern = manager.findVulCheckPattern(generatorName);
					OneShotPacket packet = pattern.getPacket();
					byte[] data = sendData.getData();
					data = manager.extractMacro(generatorName, data);
					if (data == null || data.length == 0) {
						return;
					}
					packet.setData(data);
					Date sentTime = new Date();
					ResendController.getInstance().resend(new ResendWorker(packet, 1) {
						@Override
						protected void process(List<OneShotPacket> oneshots) {
							Date recvTime = new Date();
							try {
								for (OneShotPacket oneshot : oneshots) {
									recvPackets.put(recvPacketId, oneshot);
									recvTable.add(recvPacketId, pattern.getName(), oneshot,
											recvTime.getTime() - sentTime.getTime());
									recvPacketId++;
								}
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

		JButton sendAllButton = new JButton("send all");
		sendAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					CompletableFuture<String> future = CompletableFuture.completedFuture("send all packets");
					for (VulCheckPattern pattern : manager.getAllEnabledVulCheckPattern()) {
						future = future.thenApplyAsync(arg -> {
							try {
								Date sentTime = new Date();
								OneShotPacket packet = pattern.getPacket();
								packet.setData(manager.extractMacro(pattern.getName(), packet.getData()));
								ResendController.getInstance().resend(new ResendWorker(packet, 1) {
									@Override
									protected void process(List<OneShotPacket> oneshots) {
										Date recvTime = new Date();
										try {
											for (OneShotPacket res : oneshots) {
												recvPackets.put(recvPacketId, res);
												recvTable.add(recvPacketId, pattern.getName(), res,
														recvTime.getTime() - sentTime.getTime());
												recvPacketId++;
											}
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								});
							} catch (Exception e1) {
								e1.printStackTrace();
							}
							return arg;
						});
						future = future.thenApplyAsync(arg -> {
							try {
								Thread.sleep(100); // wait 0.1 sec before sending next packet
							} catch (Exception e1) {
								e1.printStackTrace();
							}
							return arg;
						});
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(sendButton);
		buttonPanel.add(sendAllButton);
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

		JPanel bottomHalf = new JPanel();
		bottomHalf.add(sendData.getTabPanel());
		bottomHalf.add(buttonPanel);
		bottomHalf.setLayout(new BoxLayout(bottomHalf, BoxLayout.Y_AXIS));

		JSplitPane split_panel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split_panel.add(sendTable.createPanel());
		split_panel.add(bottomHalf);
		split_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
		split_panel.setDividerLocation(200);
		return split_panel;
	}

	private JComponent createRecvPanel() throws Exception {
		recvData = new TabSet(true, false);
		recvTable = new GUIVulCheckRecvTable(oneshotId -> {
			selectedRecvPacketId = oneshotId;
			OneShotPacket pkt = recvPackets.get(oneshotId);
			if (pkt != null)
				recvData.setData(pkt.getData());
		});
		JSplitPane split_panel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split_panel.add(recvTable.createPanel());
		split_panel.add(recvData.getTabPanel());
		split_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
		split_panel.setDividerLocation(200);
		return split_panel;
	}

}
