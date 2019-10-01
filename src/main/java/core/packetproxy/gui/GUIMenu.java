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

import packetproxy.model.Database;
import packetproxy.model.Packets;

@SuppressWarnings("serial")
public class GUIMenu extends JMenuBar {
	final static private String defaultDir = System.getProperty("user.home");
	GUIMenu self;
	JFrame owner;
	public GUIMenu(JFrame owner) {
		self = this;
		this.owner = owner;
		JMenu file_menu = new JMenu("プロジェクト");
		this.add(file_menu);
		JMenuItem save_sqlite = new JMenuItem("ローカル保存(sqlite3)", KeyEvent.VK_S);
		file_menu.add(save_sqlite);
		save_sqlite.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					@SuppressWarnings("serial")
					JFileChooser filechooser = new JFileChooser() {
						@Override
						public void approveSelection(){
							File f = getSelectedFile();
							if (f.exists() && getDialogType() == SAVE_DIALOG) {
								int result = JOptionPane.showConfirmDialog(this, "ファイルが既に存在しますが上書きしますか？" ,"Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
								switch(result){
									case JOptionPane.YES_OPTION:
										super.approveSelection();
										return;
									case JOptionPane.NO_OPTION:
										return;
									case JOptionPane.CLOSED_OPTION:
										return;
									case JOptionPane.CANCEL_OPTION:
										cancelSelection();
										return;
								}
							}
							super.approveSelection();
						}
					};
					filechooser.setCurrentDirectory(new File(defaultDir));
					filechooser.setFileFilter(new FileNameExtensionFilter("*.sqlite3", "sqlite3"));
					filechooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					int selected = filechooser.showSaveDialog(SwingUtilities.getRoot(self));
					if (selected == JFileChooser.APPROVE_OPTION) {
						File file = filechooser.getSelectedFile();
						if (file.getName().matches(".+\\.sqlite3")) {
							Database.getInstance().Save(file.getAbsolutePath());
						} else {
							Database.getInstance().Save(file.getAbsolutePath() + ".sqlite3");
						}
						JOptionPane.showMessageDialog(null, "データを保存しました。");
					}
				} catch (Exception e1) {
					e1.printStackTrace();
					JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
				}
			}
		});
		JMenuItem save_txt = new JMenuItem("ローカル保存(txt)", KeyEvent.VK_S);
		file_menu.add(save_txt);
		save_txt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					@SuppressWarnings("serial")
					JFileChooser filechooser = new JFileChooser() {
						@Override
						public void approveSelection(){
							File f = getSelectedFile();
							if (f.exists() && getDialogType() == SAVE_DIALOG) {
								int result = JOptionPane.showConfirmDialog(this, "ファイルが既に存在しますが上書きしますか？" ,"Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
								switch(result){
									case JOptionPane.YES_OPTION:
										super.approveSelection();
										return;
									case JOptionPane.NO_OPTION:
										return;
									case JOptionPane.CLOSED_OPTION:
										return;
									case JOptionPane.CANCEL_OPTION:
										cancelSelection();
										return;
								}
							}
							super.approveSelection();
						}
					};
					filechooser.setCurrentDirectory(new File(defaultDir));
					filechooser.setFileFilter(new FileNameExtensionFilter("*.txt", "txt"));
					filechooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					int selected = filechooser.showSaveDialog(SwingUtilities.getRoot(self));
					if (selected == JFileChooser.APPROVE_OPTION) {
						File file = filechooser.getSelectedFile();
						String fn;
						if (file.getName().matches(".+\\.txt")) {
							fn = Packets.getInstance().outputAllPackets(file.getAbsolutePath());
						} else {
							fn = Packets.getInstance().outputAllPackets(file.getAbsolutePath() + ".txt");
						}
						JOptionPane.showMessageDialog (null, fn + "に保存しました");
					}
				} catch (Exception e1) {
					e1.printStackTrace();
					JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
				}
			}
		});
		JMenuItem load_menu = new JMenuItem("ローカル読込", KeyEvent.VK_L);
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
					JOptionPane.showMessageDialog(null, "データの読み込みに失敗しました。");
				}
			}
		});
	}
}
