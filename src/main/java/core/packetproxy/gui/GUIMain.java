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

import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.model.InterceptModel;
import packetproxy.util.PacketProxyUtility;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Observable;
import java.util.Observer;

import static javax.swing.JOptionPane.YES_NO_OPTION;

public class GUIMain extends JFrame implements Observer
{
	private static final long serialVersionUID = 1L;
	private static GUIMain instance;
	private GUIMenu menu_bar;
	private JTabbedPane tabbedpane;
	private GUIOption gui_option;
	private GUIHistory gui_history;
	private GUIIntercept gui_intercept;
	private GUIRepeater gui_repeater;
	private GUIBulkSender gui_bulksender;
	private GUIExtensions gui_extensions;
	private GUIVulCheckHelper gui_vulcheckhelper;
	private GUILog gui_log;
	private InterceptModel interceptModel;
	public enum Panes {HISTORY, INTERCEPT, REPEATER, VULCHECKHELPER, BULKSENDER, EXTENSIONS, OPTIONS, LOG};

	public static GUIMain getInstance(String title) throws Exception
	{
		if (instance == null) {
			instance = new GUIMain(title);
		}
		return instance;
	}

	public static GUIMain getInstance() throws Exception
	{
		if (instance == null) {
			throw new Exception("GUIMain instance not found.");
		}
		return instance;
	}

	public JTabbedPane getTabbedPane()
	{
		return this.tabbedpane;
	}
	
	private String getPaneString(Panes num) {
		switch (num) {
		case HISTORY:
			return "History";
		case INTERCEPT:
			return "Interceptor";
		case REPEATER:
			return "Resender";
		case VULCHECKHELPER:
			return "VulCheck Helper";
		case BULKSENDER:
			return "Bulk Sender";
		case EXTENSIONS:
			return "Extensions";
		case OPTIONS:
			return "Options";
		case LOG:
			return "Log";
		}
		return null;
	}

	private GUIMain(String title) {
		try {
			setLookandFeel();
			setTitle(title);
			setBounds(10, 10, 1100, 850);
			enableFullScreenForMac(this);

			menu_bar = new GUIMenu(this);
			setJMenuBar(menu_bar);

			gui_option = new GUIOption(this);
			gui_history = getGUIHistory();
			gui_intercept = new GUIIntercept(this);
			gui_repeater = GUIRepeater.getInstance();
			gui_bulksender = GUIBulkSender.getInstance();
			gui_extensions = GUIExtensions.getInstance();
			gui_vulcheckhelper = GUIVulCheckHelper.getInstance();
			gui_log = GUILog.getInstance();

			tabbedpane = new JTabbedPane();
			tabbedpane.addTab(getPaneString(Panes.HISTORY), gui_history.createPanel());
			tabbedpane.addTab(getPaneString(Panes.INTERCEPT), gui_intercept.createPanel());
			tabbedpane.addTab(getPaneString(Panes.REPEATER), gui_repeater.createPanel());
			tabbedpane.addTab(getPaneString(Panes.VULCHECKHELPER), gui_vulcheckhelper.createPanel());
			tabbedpane.addTab(getPaneString(Panes.BULKSENDER), gui_bulksender.createPanel());
			tabbedpane.addTab(getPaneString(Panes.EXTENSIONS), gui_extensions.createPanel());
			tabbedpane.addTab(getPaneString(Panes.OPTIONS), gui_option.createPanel());
			tabbedpane.addTab(getPaneString(Panes.LOG), gui_log.createPanel());

			getContentPane().add(tabbedpane, BorderLayout.CENTER);

			interceptModel = InterceptModel.getInstance();
			interceptModel.addObserver(this);
			final Container cp = getContentPane();

			//// 終了時の処理
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent event) {
					System.exit(0);
				}
			});
			gui_history.updateAllAsync();
		} catch (Exception e) {
			PacketProxyUtility.getInstance().packetProxyLogErrWithStackTrace(e);
			e.printStackTrace();
		}
	}

	private GUIHistory getGUIHistory() throws Exception {
		// 環境変数RESTORE_HISTORYで指定されている場合promptせずに起動
		String restoreHistoryEnv = System.getenv("RESTORE_HISTORY");
		if (restoreHistoryEnv != null) {
			if (restoreHistoryEnv.matches("(?i)(true|yes|y|restore)")) {
				return GUIHistory.restoreLastInstance(this);
			} else if (restoreHistoryEnv.matches("(?i)(false|no|n|drop)")) {
				return GUIHistory.getInstance(this);
			}
		}

		// 環境変数RESTORE_HISTORYで指定されていなかった場合Historyをrestoreするか聞く
		int restoreHistory = JOptionPane.showConfirmDialog(this,
				I18nString.get("Do you want to load the previous packet data?"),
				I18nString.get("Loading previous packet data"),
				YES_NO_OPTION);
		if (restoreHistory == YES_NO_OPTION) {
			return GUIHistory.restoreLastInstance(this);
		} else {
			return GUIHistory.getInstance(this);
		}
	}

	private void setLookandFeel() throws Exception {
		
		if (PacketProxyUtility.getInstance().isUnix()) {	
			System.setProperty("awt.useSystemAAFontSettings", "on");
			System.setProperty("swing.aatext", "true");
		}
		
		for (LookAndFeelInfo clInfo : UIManager.getInstalledLookAndFeels()) {
			if ("Nimbus".equals(clInfo.getName())) {
				UIManager.setLookAndFeel(clInfo.getClassName());
				break;
			}
		}

		// フォントの設定
		UIManager.getLookAndFeelDefaults().put("defaultFont", FontManager.getInstance().getUIFont());
		// OptionPaneのロケール
		JOptionPane.setDefaultLocale(I18nString.getLocale());

		setIconForWindows();
		addShortcutForWindows();
		addShortcutForMac();
		addDockIconForMac();
	}
	
	/**
	 * Windowsにアイコンを表示する
	 */
	private void setIconForWindows() throws Exception {
		if (!PacketProxyUtility.getInstance().isWindows()) {
			return;
		}
		ImageIcon icon = new ImageIcon(getClass().getResource("/gui/icon.png"));
		setIconImage(icon.getImage());
	}

	/**
	 * MacのDock上でにPacketProxyアイコンを表示する
	 */
	private void addDockIconForMac() throws Exception {
		if (!PacketProxyUtility.getInstance().isMac()) {
			return;
		}
		ImageIcon icon = new ImageIcon(getClass().getResource("/gui/icon.png"));
		Taskbar.getTaskbar().setIconImage(icon.getImage());
	}

	/**
	 * JTextPane上でCommand+Cとかでコピペをできるようにする
	 */
	private void addShortcutForMac() {
		if (!PacketProxyUtility.getInstance().isMac()) {
			return;
		}
		JPanel p = (JPanel) getContentPane();
		InputMap im = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = p.getActionMap();
		int hotkey = (KeyEvent.CTRL_MASK | KeyEvent.META_MASK);
		registerTabShortcut(KeyEvent.VK_H, hotkey, im, am, Panes.HISTORY.ordinal());
		registerTabShortcut(KeyEvent.VK_I, hotkey, im, am, Panes.INTERCEPT.ordinal());
		registerTabShortcut(KeyEvent.VK_R, hotkey, im, am, Panes.REPEATER.ordinal());
		registerTabShortcut(KeyEvent.VK_B, hotkey, im, am, Panes.BULKSENDER.ordinal());
		registerTabShortcut(KeyEvent.VK_O, hotkey, im, am, Panes.OPTIONS.ordinal());
		registerTabShortcut(KeyEvent.VK_L, hotkey, im, am, Panes.LOG.ordinal());

		JTextComponent.KeyBinding[] bindings1 = {
			new JTextComponent.KeyBinding(
					KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),
					DefaultEditorKit.copyAction),
			new JTextComponent.KeyBinding(
					KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),
					DefaultEditorKit.pasteAction),
			new JTextComponent.KeyBinding(
					KeyStroke.getKeyStroke(KeyEvent.VK_X, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),
					DefaultEditorKit.cutAction),
			new JTextComponent.KeyBinding(
					KeyStroke.getKeyStroke(KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()),
					DefaultEditorKit.selectAllAction),
		};

		JTextPane component_tp = new JTextPane();
		Keymap keymap_tp = component_tp.getKeymap();
		JTextComponent.loadKeymap(keymap_tp, bindings1, component_tp.getActions());

		JTextField component_tf = new JTextField();
		Keymap keymap_tf = component_tf.getKeymap();
		JTextComponent.loadKeymap(keymap_tf, bindings1, component_tf.getActions());

		JTextArea component_ta = new JTextArea();
		Keymap keymap_ta = component_ta.getKeymap();
		JTextComponent.loadKeymap(keymap_ta, bindings1, component_ta.getActions());
	}

	private void addShortcutForWindows() {
		if (PacketProxyUtility.getInstance().isMac()) {
			return;
		}
		JPanel p = (JPanel) getContentPane();
		InputMap im = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = p.getActionMap();
		int hotkey = (KeyEvent.CTRL_MASK);
		registerTabShortcut(KeyEvent.VK_H, hotkey, im, am, Panes.HISTORY.ordinal());
		registerTabShortcut(KeyEvent.VK_I, hotkey, im, am, Panes.INTERCEPT.ordinal());
		registerTabShortcut(KeyEvent.VK_R, hotkey, im, am, Panes.REPEATER.ordinal());
		registerTabShortcut(KeyEvent.VK_B, hotkey, im, am, Panes.BULKSENDER.ordinal());
		registerTabShortcut(KeyEvent.VK_O, hotkey, im, am, Panes.OPTIONS.ordinal());
		registerTabShortcut(KeyEvent.VK_L, hotkey, im, am, Panes.LOG.ordinal());
	}

	private void registerTabShortcut(int k, int m, InputMap im, ActionMap am, int index) {
		KeyStroke ks = KeyStroke.getKeyStroke(k, m);
		im.put(ks, ks.toString());
		am.put(ks.toString(), new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				tabbedpane.setSelectedIndex(index);
			}
		});
	}

	/**
	 * Macでフルスクリーン表示できるようにする
	 */
	private void enableFullScreenForMac(Window window) throws Exception {
		if (!PacketProxyUtility.getInstance().isMac()) {
			return;
		}
		getRootPane().putClientProperty("apple.awt.fullscreenable", true);
	}

	// Nimbusのバグでjava1.6系列ではsetForegroundAt, setBackgroundAtが効かない
	// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6939001
	private void setInterceptHighLight()
	{
		JLabel label = new JLabel(tabbedpane.getTitleAt(1));
		label.setForeground(Color.ORANGE);
		tabbedpane.setTabComponentAt(1, label);
		tabbedpane.revalidate();
		tabbedpane.repaint();
	}
	private void setInterceptDownLight()
	{
		JLabel label = new JLabel(tabbedpane.getTitleAt(1));
		label.setForeground(Color.BLACK);
		tabbedpane.setTabComponentAt(1, label);
		tabbedpane.revalidate();
		tabbedpane.repaint();
	}
	@Override
	public void update(Observable arg0, Object arg1) {
		if (interceptModel.getData() == null) {
			setInterceptDownLight();
		} else {
			setInterceptHighLight();
		}
	}
}
