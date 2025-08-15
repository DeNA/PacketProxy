/*
 * Copyright 2025 DeNA Co., Ltd.
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

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import org.apache.commons.io.FileUtils;
import packetproxy.controller.ResendController;
import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.util.CharSetUtility;

/**
 * Extracted popup menu builder and action wiring for GUIHistory. Reduces the
 * size and responsibility in GUIHistory.java.
 */
public class GUIHistoryContextMenuFactory {

	private GUIHistoryContextMenuFactory() {
	}

	public static class Handles {
		private final JPopupMenu menu;
		private final JMenuItem send;
		private final JMenuItem sendToResender;
		private final JMenuItem copy;
		private final JMenuItem copyAll;

		Handles(JPopupMenu menu, JMenuItem send, JMenuItem sendToResender, JMenuItem copy, JMenuItem copyAll) {
			this.menu = menu;
			this.send = send;
			this.sendToResender = sendToResender;
			this.copy = copy;
			this.copyAll = copyAll;
		}

		public JPopupMenu getMenu() {
			return menu;
		}

		public JMenuItem getSend() {
			return send;
		}

		public JMenuItem getSendToResender() {
			return sendToResender;
		}

		public JMenuItem getCopy() {
			return copy;
		}

		public JMenuItem getCopyAll() {
			return copyAll;
		}
	}

	public static Handles build(GUIHistory context, JFrame owner, JTable table, GUIPacket gui_packet, Packets packets,
			TableCustomColorManager colorManager, Color packetColorGreen, Color packetColorBrown,
			Color packetColorYellow) {
		final int mask_key = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		JPopupMenu menu = new JPopupMenu();

		JMenuItem send = createMenuItem("send", KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_S, mask_key), e -> {
			try {
				int id = context.getSelectedPacketId();
				Packet packet = packets.query(id);
				byte[] data = gui_packet.getData();
				if (packet == null) {
					return;
				}
				ResendController.getInstance().resend(packet.getOneShotPacket(data));
				packet.setResend();
				packets.update(packet);
				context.updateRequestOne(context.getSelectedPacketId());
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem sendToResender = createMenuItem("send to Resender", KeyEvent.VK_R,
				KeyStroke.getKeyStroke(KeyEvent.VK_R, mask_key), e -> {
					try {
						Packet packet = gui_packet.getPacket();
						packet.setResend();
						packets.update(packet);
						if (packet.getModifiedData().length == 0) {
							GUIResender.getInstance().addResends(packet.getOneShotFromDecodedData());
						} else {
							GUIResender.getInstance().addResends(packet.getOneShotFromModifiedData());
						}
						context.updateRequestOne(context.getSelectedPacketId());
					} catch (Exception ex) {
						errWithStackTrace(ex);
					}
				});

		JMenuItem copyAll = createMenuItem("copy Method + URL + Body", KeyEvent.VK_M,
				KeyStroke.getKeyStroke(KeyEvent.VK_M, mask_key), e -> {
					try {
						Packet packet = gui_packet.getPacket();
						Http http = Http.create(packet.getDecodedData());
						CharSetUtility charsetutil = CharSetUtility.getInstance();
						if (charsetutil.isAuto()) {
							charsetutil.setGuessedCharSet(http.getBody());
						}
						String copyData = http.getMethod() + "\t"
								+ http.getURL(packet.getServerPort(), packet.getUseSSL()) + "\t"
								+ new String(http.getBody(), charsetutil.getCharSet());
						Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
						StringSelection selection = new StringSelection(copyData);
						clipboard.setContents(selection, selection);
					} catch (Exception ex) {
						errWithStackTrace(ex);
					}
				});

		JMenuItem copy = createMenuItem("copy URL", KeyEvent.VK_Y, KeyStroke.getKeyStroke(KeyEvent.VK_Y, mask_key),
				e -> {
					try {
						int id = context.getSelectedPacketId();
						Packet packet = packets.query(id);
						Http http = Http.create(packet.getDecodedData());
						String url = http.getURL(packet.getServerPort(), packet.getUseSSL());
						Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
						StringSelection selection = new StringSelection(url);
						clipboard.setContents(selection, selection);
					} catch (Exception ex) {
						errWithStackTrace(ex);
					}
				});

		JMenuItem bulkSender = createMenuItem("send to Bulk Sender", -1, null, e -> {
			try {
				Packet packet = gui_packet.getPacket();
				if (packet.getModifiedData().length == 0) {
					GUIBulkSender.getInstance().add(packet.getOneShotFromDecodedData(), packet.getId());
				} else {
					GUIBulkSender.getInstance().add(packet.getOneShotFromModifiedData(), packet.getId());
				}
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem saveAll = createMenuItem("save all data to file", -1, null, e -> {
			WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "dat", "packet.dat");
			filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {
				@Override
				public void onApproved(File file, String extension) {
					try {
						byte[] data = gui_packet.getPacket().getReceivedData();
						FileUtils.writeByteArrayToFile(file, data);
						JOptionPane.showMessageDialog(owner, String.format("%sに保存しました！", file.getPath()));
					} catch (Exception ex) {
						errWithStackTrace(ex);
						JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
					}
				}

				@Override
				public void onCanceled() {
				}

				@Override
				public void onError() {
					JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
				}
			});
			filechooser.showSaveDialog();
		});

		JMenuItem saveHttpBody = createMenuItem("save HTTP body to file", -1, null, e -> {
			WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "dat", "body.dat");
			filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {
				@Override
				public void onApproved(File file, String extension) {
					try {
						Http http = Http.create(gui_packet.getPacket().getDecodedData());
						byte[] data = http.getBody();
						FileUtils.writeByteArrayToFile(file, data);
						JOptionPane.showMessageDialog(owner, String.format("%sに保存しました！", file.getPath()));
					} catch (Exception ex) {
						errWithStackTrace(ex);
						JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
					}
				}

				@Override
				public void onCanceled() {
				}

				@Override
				public void onError() {
					JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
				}
			});
			filechooser.showSaveDialog();
		});

		JMenuItem addColorG = createMenuItem("add color (green)", -1, null, e -> {
			try {
				int[] selected_rows = table.getSelectedRows();
				for (int i = 0; i < selected_rows.length; i++) {
					Integer id = (Integer) table.getValueAt(selected_rows[i], 0);
					colorManager.add(id, packetColorGreen);
					Packet packet = packets.query(id);
					packet.setColor("green");
					packets.update(packet);
				}
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem addColorB = createMenuItem("add color (brown)", -1, null, e -> {
			try {
				int[] selected_rows = table.getSelectedRows();
				for (int i = 0; i < selected_rows.length; i++) {
					Integer id = (Integer) table.getValueAt(selected_rows[i], 0);
					colorManager.add(id, packetColorBrown);
					Packet packet = packets.query(id);
					packet.setColor("brown");
					packets.update(packet);
				}
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem addColorY = createMenuItem("add color (yellow)", -1, null, e -> {
			try {
				int[] selected_rows = table.getSelectedRows();
				for (int i = 0; i < selected_rows.length; i++) {
					Integer id = (Integer) table.getValueAt(selected_rows[i], 0);
					colorManager.add(id, packetColorYellow);
					Packet packet = packets.query(id);
					packet.setColor("yellow");
					packets.update(packet);
				}
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem clearColor = createMenuItem("clear color", -1, null, e -> {
			try {
				int[] selected_rows = table.getSelectedRows();
				for (int i = 0; i < selected_rows.length; i++) {
					Integer id = (Integer) table.getValueAt(selected_rows[i], 0);
					colorManager.clear(id);
				}
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem delete_selected_items = createMenuItem("delete selected items", -1, null, e -> {
			try {
				int[] selected_rows = table.getSelectedRows();
				for (int i = 0; i < selected_rows.length; i++) {
					Integer id = (Integer) table.getValueAt(selected_rows[i], 0);
					colorManager.clear(id);
					packets.delete(packets.query(id));
				}
				context.updateAll();
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem delete_all = createMenuItem("delete all items", -1, null, e -> {
			try {
				for (int i = 0; i < table.getRowCount(); ++i) {
					Integer id = (Integer) table.getValueAt(i, 0);
					colorManager.clear(id);
				}
				packets.deleteAll();
				context.updateAll();
			} catch (Exception ex) {
				errWithStackTrace(ex);
			}
		});

		JMenuItem copyAsCurl = createMenuItem("copy as curl", -1, null, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					Http http = Http.create(gui_packet.getPacket().getDecodedData());
					List<HeaderField> headerFields = http.getHeader().getFields();
					ArrayList<String> commandList = new ArrayList<>();
					commandList.add("curl");
					String url = http.getURL(gui_packet.getPacket().getServerPort(),
							gui_packet.getPacket().getUseSSL());
					commandList.add(String.format("'%s'", url));
					commandList.add("-X");
					commandList.add(http.getMethod());
					for (HeaderField hf : headerFields) {
						commandList.add("-H");
						commandList.add(String.format("'%s: %s'", hf.getName(), hf.getValue()));
					}
					String body = new String(http.getBody());
					if (body.trim().length() > 0) {
						commandList.add("--data");
						commandList.add(String.format("'%s'", body));
					}
					commandList.add("--compressed");
					StringSelection command = new StringSelection(String.join(" ", commandList));
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(command, command);
				} catch (Exception ex) {
					errWithStackTrace(ex);
				}
			}
		});

		menu.add(send);
		menu.add(sendToResender);
		menu.add(copyAll);
		menu.add(copy);
		menu.add(bulkSender);
		menu.add(saveAll);
		menu.add(saveHttpBody);
		menu.add(addColorG);
		menu.add(addColorB);
		menu.add(addColorY);
		menu.add(clearColor);
		menu.add(copyAsCurl);
		menu.add(delete_selected_items);
		menu.add(delete_all);

		return new Handles(menu, send, sendToResender, copy, copyAll);
	}

	private static JMenuItem createMenuItem(String name, int key, KeyStroke hotkey, ActionListener l) {
		JMenuItem out = new JMenuItem(name);
		if (key >= 0) {
			out.setMnemonic(key);
		}
		if (hotkey != null) {
			out.setAccelerator(hotkey);
		}
		out.addActionListener(l);
		return out;
	}
}
