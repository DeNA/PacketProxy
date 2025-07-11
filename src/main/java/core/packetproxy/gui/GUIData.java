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

import static packetproxy.util.Logging.log;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import packetproxy.controller.ResendController;
import packetproxy.http.Http;
import packetproxy.model.Diff;
import packetproxy.model.DiffBinary;
import packetproxy.model.DiffJson;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.util.CharSetUtility;

public class GUIData {

	private JFrame owner;
	private JPanel main_panel;
	private TabSet tabs;
	private JButton resend_button;
	private JButton resend_multiple_button;
	private JButton send_to_resender_button;
	private JButton copy_url_body_button;
	private JButton copy_url_button;
	private JButton copy_body_button;
	private JButton diff_orig_button;
	private JButton diff_button;
	private JButton stop_diff_button;
	private CharSetUtility charSetUtility = CharSetUtility.getInstance();
	boolean isDiff = false;
	boolean isOrigColorExists = false;
	int origIndex;
	Color origColor;
	private JComboBox charSetCombo = new JComboBox(charSetUtility.getAvailableCharSetList().toArray());

	public GUIData(JFrame owner) {
		this.owner = owner;
	}

	public JComponent createPanel() throws Exception {
		main_panel = new JPanel();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));

		tabs = new TabSet(true, false);

		main_panel.add(tabs.getTabPanel());

		copy_url_body_button = new JButton("copy Method+URL+Body");
		copy_url_body_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				try {

					int id = GUIHistory.getInstance().getSelectedPacketId();
					Packet packet = Packets.getInstance().query(id);
					Http http = Http.create(tabs.getRaw().getData());
					String copyData = http.getMethod() + "\t" + http.getURL(packet.getServerPort(), packet.getUseSSL())
							+ "\t" + new String(http.getBody(), "UTF-8");
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					StringSelection selection = new StringSelection(copyData);
					clipboard.setContents(selection, selection);
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		copy_body_button = new JButton("copy Body");
		copy_body_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					int id = GUIHistory.getInstance().getSelectedPacketId();
					Packet packet = Packets.getInstance().query(id);
					Http http = Http.create(tabs.getRaw().getData());
					String body = new String(http.getBody(), "UTF-8");// http.getURL(packet.getServerPort());
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					StringSelection selection = new StringSelection(body);
					clipboard.setContents(selection, selection);
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		copy_body_button.setAlignmentX(0.5f);

		copy_url_button = new JButton("copy URL");
		copy_url_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					int id = GUIHistory.getInstance().getSelectedPacketId();
					Packet packet = Packets.getInstance().query(id);
					Http http = Http.create(tabs.getRaw().getData());
					String url = http.getURL(packet.getServerPort(), packet.getUseSSL());
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					StringSelection selection = new StringSelection(url);
					clipboard.setContents(selection, selection);

				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		copy_url_button.setAlignmentX(0.5f);

		resend_button = new JButton("send");
		resend_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					byte[] data = null;
					int index = tabs.getSelectedIndex();
					if (index == 0) {

						data = tabs.getRaw().getData();
					} else if (index == 1) {

						data = tabs.getBinary().getData();
					}
					if (data != null) {

						int id = GUIHistory.getInstance().getSelectedPacketId();
						Packet packet = Packets.getInstance().query(id);
						ResendController.getInstance().resend(packet.getOneShotPacket(data));
						packet.setResend();
						Packets.getInstance().update(packet);
						GUIHistory.getInstance().updateRequestOne(id);
					}
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		resend_button.setAlignmentX(0.5f);

		resend_multiple_button = new JButton("send x 20");
		resend_multiple_button.setAlignmentX(0.5f);
		resend_multiple_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					byte[] data = null;
					int index = tabs.getSelectedIndex();
					if (index == 0) {

						data = tabs.getRaw().getData();
					} else if (index == 1) {

						data = tabs.getBinary().getData();
					}
					if (data != null) {

						int id = GUIHistory.getInstance().getSelectedPacketId();
						Packet packet = Packets.getInstance().query(id);
						ResendController.getInstance().resend(packet.getOneShotPacket(data), 20);
						packet.setResend();
						Packets.getInstance().update(packet);
						GUIHistory.getInstance().updateRequestOne(id);
					}
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		send_to_resender_button = new JButton("send to Resender");
		send_to_resender_button.setAlignmentX(0.5f);
		send_to_resender_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				try {

					byte[] data = null;
					int index = tabs.getSelectedIndex();
					if (index == 0) {

						data = tabs.getRaw().getData();
					} else if (index == 1) {

						data = tabs.getBinary().getData();
					}
					if (data != null) {

						int id = GUIHistory.getInstance().getSelectedPacketId();
						Packet packet = Packets.getInstance().query(id);
						packet.setResend();
						Packets.getInstance().update(packet);
						GUIResender.getInstance().addResends(packet.getOneShotPacket(data));
						GUIHistory.getInstance().updateRequestOne(id);
					}
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		stop_diff_button = new JButton("stop diff");
		stop_diff_button.setAlignmentX(0.5f);
		stop_diff_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					if (isDiff) {

						Diff.getInstance().clearAsOriginal();
						DiffBinary.getInstance().clearAsOriginal();
						DiffJson.getInstance().clearAsOriginal();
						if (isOrigColorExists) {

							isOrigColorExists = false;
							GUIHistory.getInstance().addCustomColoring(origIndex, new Color(0xb0, 0xb0, 0xb0)); // Gray
						} else {

							GUIHistory.getInstance().addCustomColoring(origIndex, Color.WHITE);
						}
						isDiff = false;
					}
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		diff_button = new JButton("diff!!");
		diff_button.setAlignmentX(0.5f);
		diff_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					Diff.getInstance().markAsTarget(tabs.getRaw().getData());
					DiffBinary.getInstance().markAsTarget(tabs.getBinary().getData());
					DiffJson.getInstance().markAsTarget(tabs.getJson().getData());
					GUIDiffDialogParent dlg = new GUIDiffDialogParent(owner);
					dlg.showDialog();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		diff_orig_button = new JButton("mark as orig");
		diff_orig_button.setAlignmentX(0.5f);
		diff_orig_button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					if (isDiff) {

						Diff.getInstance().clearAsOriginal();
						DiffBinary.getInstance().clearAsOriginal();
						DiffJson.getInstance().clearAsOriginal();
						if (isOrigColorExists) {

							isOrigColorExists = false;
							GUIHistory.getInstance().addCustomColoring(origIndex, origColor);
						} else {

							GUIHistory.getInstance().addCustomColoring(origIndex, Color.WHITE);
						}
					}
					isDiff = true;
					Diff.getInstance().markAsOriginal(tabs.getRaw().getData());
					DiffBinary.getInstance().markAsOriginal(tabs.getBinary().getData());
					DiffJson.getInstance().markAsOriginal(tabs.getJson().getData());
					if (GUIHistory.getInstance().containsColor()) {

						origColor = GUIHistory.getInstance().getColor();
						isOrigColorExists = true;
					}
					origIndex = GUIHistory.getInstance().getSelectedIndex();
					GUIHistory.getInstance().addCustomColoringToCursorPos(new Color(0xb0, 0xb0, 0xb0)); // Gray
					log("Diff: original text was saved!");
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		charSetCombo.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				charSetUtility.setCharSet((String) charSetCombo.getSelectedItem());
				try {

					GUIPacket.getInstance().update();
				} catch (Exception e2) {

					e2.printStackTrace();
				}
			}
		});
		charSetCombo.setMaximumSize(new Dimension(150, charSetCombo.getMaximumSize().height));

		charSetCombo.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				updateCharSetCombo();
			}
		});

		charSetCombo.setSelectedItem(charSetUtility.getInstance().getCharSetForGUIComponent());

		JPanel diff_panel = new JPanel();
		diff_panel.add(diff_orig_button);
		diff_panel.add(diff_button);
		diff_panel.add(stop_diff_button);
		diff_panel.setBorder(new LineBorder(Color.black, 1, true));
		diff_panel.setLayout(new BoxLayout(diff_panel, BoxLayout.LINE_AXIS));

		JPanel button_panel = new JPanel();
		button_panel.add(charSetCombo);
		button_panel.add(copy_url_body_button);
		button_panel.add(copy_body_button);
		button_panel.add(copy_url_button);
		button_panel.add(resend_button);
		button_panel.add(resend_multiple_button);
		button_panel.add(send_to_resender_button);
		button_panel.add(new JLabel("  diff: "));
		button_panel.add(diff_panel);
		button_panel.setLayout(new BoxLayout(button_panel, BoxLayout.LINE_AXIS));

		main_panel.add(button_panel);
		return main_panel;
	}

	public void updateCharSetCombo() {
		charSetCombo.removeAllItems();
		for (String charSetName : charSetUtility.getAvailableCharSetList()) {

			charSetCombo.addItem(charSetName);
		}
		String charSetName = CharSetUtility.getInstance().getCharSetForGUIComponent();
		if (charSetUtility.getAvailableCharSetList().contains(charSetName)) {

			charSetCombo.setSelectedItem(charSetName);
		} else {

			charSetCombo.setSelectedIndex(0);
		}
	}

	private void update() {
	}

	public void setData(byte[] data) {
		tabs.setData(data);
		update();
	}

	public byte[] getData() {
		if (tabs.getData() == null) {

			return new byte[]{};
		}
		try {

			return tabs.getData();
		} catch (Exception e) {

			e.printStackTrace();
		}
		return new byte[]{};
	}
}
