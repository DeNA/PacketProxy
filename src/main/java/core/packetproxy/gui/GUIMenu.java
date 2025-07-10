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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import packetproxy.common.ConfigIO;
import packetproxy.common.I18nString;
import packetproxy.common.Utils;
import packetproxy.model.Database;
import packetproxy.model.Packets;
import packetproxy.util.PacketProxyUtility;

@SuppressWarnings("serial")
public class GUIMenu extends JMenuBar {

	private static final String defaultDir = System.getProperty("user.home");
	GUIMenu self;
	JFrame owner;
	private enum Panes {
		HISTORY, INTERCEPT, RESENDER, BULKSENDER, OPTIONS, LOG
	};
	public GUIMenu(JFrame owner) {
		self = this;
		this.owner = owner;
		JMenu file_menu = new JMenu(I18nString.get("Project"));
		this.add(file_menu);
		JMenuItem save_sqlite = new JMenuItem(I18nString.get("Save packets to sqlite3 file"), KeyEvent.VK_S);
		file_menu.add(save_sqlite);
		save_sqlite.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "sqlite3");
				filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {

					@Override
					public void onApproved(File file, String extension) {
						try {

							Database.getInstance().Save(file.getAbsolutePath());
							JOptionPane.showMessageDialog(null, I18nString.get("Data saved successfully"));
						} catch (Exception e1) {

							e1.printStackTrace();
							JOptionPane.showMessageDialog(null, I18nString.get("Data can't be saved with error"));
						}
					}

					@Override
					public void onCanceled() {
					}

					@Override
					public void onError() {
						JOptionPane.showMessageDialog(null, I18nString.get("Data can't be saved with error"));
					}
				});
				filechooser.showSaveDialog();
			}
		});
		JMenuItem save_txt = new JMenuItem(I18nString.get("Save packets to text file"), KeyEvent.VK_S);
		file_menu.add(save_txt);
		save_txt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {

				WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "txt");
				filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {

					@Override
					public void onApproved(File file, String extension) {
						try {

							Packets.getInstance().outputAllPackets(file.getAbsolutePath());
							JOptionPane.showMessageDialog(null, I18nString.get("Data saved successfully"));
						} catch (Exception e1) {

							e1.printStackTrace();
							JOptionPane.showMessageDialog(null, I18nString.get("Data can't be saved with error"));
						}
					}

					@Override
					public void onCanceled() {
					}

					@Override
					public void onError() {
						JOptionPane.showMessageDialog(null, I18nString.get("Data can't be saved with error"));
					}
				});
				filechooser.showSaveDialog();
			}
		});
		JMenuItem load_menu = new JMenuItem(I18nString.get("Load packets from sqlite3 file"), KeyEvent.VK_L);
		file_menu.add(load_menu);
		load_menu.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					JFileChooser filechooser = new JFileChooser();
					filechooser.setCurrentDirectory(new File(defaultDir));
					filechooser.setFileFilter(new FileNameExtensionFilter("*.sqlite3", "sqlite3"));
					filechooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					int selected = filechooser.showOpenDialog(SwingUtilities.getRoot(self));
					if (selected == JFileChooser.APPROVE_OPTION) {

						File file = filechooser.getSelectedFile();
						Database.getInstance().Load(file.getAbsolutePath());
					}
				} catch (Exception e1) {

					e1.printStackTrace();
					JOptionPane.showMessageDialog(null, I18nString.get("Data can't be loaded with error"));
				}
			}
		});

		String cmd_key = "⌘ ^ ";
		if (!PacketProxyUtility.getInstance().isMac()) {

			cmd_key = "Ctrl + ";
		}
		JMenu view_menu = new JMenu(I18nString.get("View"));
		this.add(view_menu);
		JMenuItem view_history = new JMenuItem(I18nString.get("View History") + "  " + cmd_key + "H");
		view_menu.add(view_history);
		view_history.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIMain.getInstance().getTabbedPane().setSelectedIndex(Panes.HISTORY.ordinal());

				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		JMenuItem view_intercept = new JMenuItem(I18nString.get("View Interceptor") + "  " + cmd_key + "I");
		view_menu.add(view_intercept);
		view_intercept.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIMain.getInstance().getTabbedPane().setSelectedIndex(Panes.INTERCEPT.ordinal());

				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		JMenuItem view_resender = new JMenuItem(I18nString.get("View Resender") + "  " + cmd_key + "R");
		view_menu.add(view_resender);
		view_resender.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIMain.getInstance().getTabbedPane().setSelectedIndex(Panes.RESENDER.ordinal());

				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		JMenuItem view_bulk_sender = new JMenuItem(I18nString.get("View BulkSender") + "  " + cmd_key + "B");
		view_menu.add(view_bulk_sender);
		view_bulk_sender.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIMain.getInstance().getTabbedPane().setSelectedIndex(Panes.BULKSENDER.ordinal());
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		JMenuItem view_options = new JMenuItem(I18nString.get("View Options") + "  " + cmd_key + "O");
		view_menu.add(view_options);
		view_options.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIMain.getInstance().getTabbedPane().setSelectedIndex(Panes.OPTIONS.ordinal());
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		JMenuItem view_log = new JMenuItem(I18nString.get("View Log") + "  " + cmd_key + "L");
		view_menu.add(view_log);
		view_log.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIMain.getInstance().getTabbedPane().setSelectedIndex(Panes.LOG.ordinal());
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		JMenu config_menu = new JMenu(I18nString.get("Options"));
		this.add(config_menu);
		JMenuItem import_configs = new JMenuItem(I18nString.get("Import Configs"));
		config_menu.add(import_configs);
		import_configs.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					JFileChooser filechooser = new JFileChooser();
					filechooser.setCurrentDirectory(new File(defaultDir));
					filechooser.setFileFilter(new FileNameExtensionFilter("*.json", "json"));
					filechooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					int selected = filechooser.showOpenDialog(SwingUtilities.getRoot(self));
					if (selected == JFileChooser.APPROVE_OPTION) {

						File file = filechooser.getSelectedFile();
						byte[] jbytes = Utils.readfile(file.getAbsolutePath());
						String json = new String(jbytes);
						ConfigIO io = new ConfigIO();
						io.setOptions(json);
						JOptionPane.showMessageDialog(null, I18nString.get("Config loaded successfully"));
					}
				} catch (Exception e1) {

					e1.printStackTrace();
					JOptionPane.showMessageDialog(null, I18nString.get("Config can't be loaded with error"));
				}
			}
		});
		JMenuItem export_configs = new JMenuItem(I18nString.get("Export Configs"));
		config_menu.add(export_configs);
		export_configs.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "json");
					filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {

						@Override
						public void onApproved(File file, String extension) {
							try {

								ConfigIO io = new ConfigIO();
								String json = io.getOptions();
								Utils.writefile(file.getAbsolutePath(), json.getBytes());
								JOptionPane.showMessageDialog(null, I18nString.get("Config saved successfully"));
							} catch (Exception e1) {

								e1.printStackTrace();
								JOptionPane.showMessageDialog(null, I18nString.get("Config can't be saved with error"));
							}
						}

						@Override
						public void onCanceled() {
						}

						@Override
						public void onError() {
							JOptionPane.showMessageDialog(null, I18nString.get("Config can't be saved with error"));
						}
					});
					filechooser.showSaveDialog();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
	}
}
