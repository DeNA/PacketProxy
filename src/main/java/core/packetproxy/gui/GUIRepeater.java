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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import packetproxy.controller.ResendController;
import packetproxy.controller.ResendController.ResendWorker;
import packetproxy.model.OneShotPacket;

public class GUIRepeater
{
	private JPanel main_panel;
	private CloseButtonTabbedPane repeat_tab;
	private List<Repeats> list;
	private int previousTabIndex;

	private static GUIRepeater instance;
	
	public static GUIRepeater getInstance() throws Exception {
		if (instance == null) {
			instance = new GUIRepeater();
		}
		return instance;
	}

	private GUIRepeater() {
		main_panel = new JPanel();
		repeat_tab = new CloseButtonTabbedPane();
		previousTabIndex = repeat_tab.getSelectedIndex();
		repeat_tab.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int currentTabIndex = repeat_tab.getSelectedIndex();
				if(previousTabIndex<0){
					previousTabIndex = currentTabIndex;
					return;
				}
				previousTabIndex = currentTabIndex;
			}
		});
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		main_panel.add(repeat_tab);
		list = new ArrayList<Repeats>();
	}
	
	public JComponent createPanel() {
		return main_panel;
	}
	
	public void addRepeats(OneShotPacket send_packet) throws Exception {
		Repeats repeats = new Repeats(this);
		list.add(repeats);
		repeat_tab.addTab(String.valueOf(list.size()+1), repeats.getComponent());
		repeat_tab.setSelectedComponent(repeats.getComponent());
		repeats.addRepeat(send_packet, null);
	}
	
	public Repeats get(int index) {
		return list.get(index);
	}
	
	class Repeats {
		private GUIRepeater parent;
		private JPanel main_panel;
		private CloseButtonTabbedPane repeat_tab;
		private List<Repeat> list;
		private int previousTabIndex;
		
		public Repeats(GUIRepeater parent) {
			this.parent = parent;
			repeat_tab = new CloseButtonTabbedPane();
			previousTabIndex = repeat_tab.getSelectedIndex();
			repeat_tab.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					int currentTabIndex = repeat_tab.getSelectedIndex();
					if(previousTabIndex<0){
						previousTabIndex = currentTabIndex;
						return;
					}
					previousTabIndex = currentTabIndex;
				}
			});
		    main_panel = new JPanel();
			main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
			main_panel.add(repeat_tab);
			list = new ArrayList<Repeat>();
		}

		public void addRepeat(OneShotPacket send_packet, OneShotPacket recv_packet) throws Exception {
			Repeat repeat = new Repeat(this);
			list.add(repeat);
			repeat_tab.addTab(String.valueOf(list.size() + 1), repeat.getComponent());
			repeat_tab.setSelectedComponent(repeat.getComponent());
			repeat.setOneShotPacket(send_packet, recv_packet);
		}

		public Repeat get(int index) {
			return list.get(index);
		}

		public JComponent getComponent() {
			return main_panel;
		}
	}
	
	class Repeat implements Observer {
		private GUIServerNamePanel server_name_panel;
		private OneShotPacket send_saved;
		private OneShotPacket recv_saved;
		private GUIPacketData send_panel;
		private GUIPacketData recv_panel;
		private JSplitPane split_panel;
		private JButton resend_button;
		private JButton resend_multiple_button;
		private JPanel main_panel;

		public Repeat(Repeats parent) {
		
			server_name_panel = new GUIServerNamePanel();
			send_panel = new GUIPacketData();
			recv_panel = new GUIPacketData();


			send_panel.getTabs().addObserver(this);
			split_panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
			split_panel.setBackground(Color.WHITE);
			split_panel.add(send_panel.createPanel());
			split_panel.add(recv_panel.createPanel());
			split_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
			split_panel.setResizeWeight(0.5);
			resend_button = new JButton("send");
			resend_button.addActionListener(new ActionListener(){
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
									parent.addRepeat(sendPacket, recvPacket);
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
			
		    JPanel button_panel = new JPanel();
			button_panel.add(resend_button);
			button_panel.add(resend_multiple_button);
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
			send_panel.setOneShotPacket(send_saved == null ? null : (OneShotPacket)send_saved.clone());
			recv_panel.setOneShotPacket(recv_saved == null ? null : (OneShotPacket)recv_saved.clone());
		}

		public void setOneShotPacket(OneShotPacket send_packet, OneShotPacket recv_packet) throws Exception {
			send_saved = send_packet == null ? null : (OneShotPacket)send_packet.clone();
			recv_saved = recv_packet == null ? null : (OneShotPacket)recv_packet.clone();
			send_panel.setOneShotPacket(send_packet);
			recv_panel.setOneShotPacket(recv_packet);
			server_name_panel.updateServerName(send_packet);
		}

		@Override
		public void update(Observable arg0, Object arg1) {
			if (arg0.getClass() == TabSet.class) {
				if ((int)arg1 == 2) {
					resend_button.setEnabled(false);
					resend_multiple_button.setEnabled(false);
				} else {
					resend_button.setEnabled(true);
					resend_multiple_button.setEnabled(true);
				}
			}
		}
	}
}
