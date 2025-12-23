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

import static packetproxy.util.Logging.errWithStackTrace;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import com.formdev.flatlaf.FlatIntelliJLaf;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.model.InterceptModel;
import packetproxy.util.PacketProxyUtility;

public class GUIMain extends JFrame implements PropertyChangeListener {

	private static final long serialVersionUID = 1L;
	private static GUIMain instance;
	private GUIMenu menu_bar;
	private JTabbedPane tabbedpane;
	private GUIOption gui_option;
	private GUIHistory gui_history;
	private GUIIntercept gui_intercept;
	private GUIResender gui_resender;
	private GUIBulkSender gui_bulksender;
	private GUIExtensions gui_extensions;
	private GUIVulCheckHelper gui_vulcheckhelper;
	private GUILog gui_log;
	private InterceptModel interceptModel;

	public enum Panes {
		HISTORY, INTERCEPT, RESENDER, VULCHECKHELPER, BULKSENDER, EXTENSIONS, OPTIONS, LOG
	};

	public static GUIMain getInstance(String title) throws Exception {
		if (instance == null) {

			instance = new GUIMain(title);
		}
		return instance;
	}

	public static GUIMain getInstance() throws Exception {
		if (instance == null) {

			throw new Exception("GUIMain instance not found.");
		}
		return instance;
	}

	public JTabbedPane getTabbedPane() {
		return this.tabbedpane;
	}

	private String getPaneString(Panes num) {
		switch (num) {
			case HISTORY :
				return "History";
			case INTERCEPT :
				return "Interceptor";
			case RESENDER :
				return "Resender";
			case VULCHECKHELPER :
				return "VulCheck Helper";
			case BULKSENDER :
				return "Bulk Sender";
			case EXTENSIONS :
				return "Extensions";
			case OPTIONS :
				return "Options";
			case LOG :
				return "Log";
		}
		return null;
	}

	private GUIMain(String title) {
		try {

			gui_history = initProjectAndHistory();
			setLookandFeel();
			setTitle(title);
			setBounds(10, 10, 1100, 850);
			enableFullScreenForMac(this);

			menu_bar = new GUIMenu(this);
			setJMenuBar(menu_bar);

			gui_option = new GUIOption(this);
			gui_intercept = new GUIIntercept(this);
			gui_resender = GUIResender.getInstance();
			gui_bulksender = GUIBulkSender.getInstance();
			gui_extensions = GUIExtensions.getInstance();
			gui_vulcheckhelper = GUIVulCheckHelper.getInstance();
			gui_log = GUILog.getInstance();

			tabbedpane = new JTabbedPane();
			tabbedpane.addTab(getPaneString(Panes.HISTORY), gui_history.createPanel());
			tabbedpane.addTab(getPaneString(Panes.INTERCEPT), gui_intercept.createPanel());
			tabbedpane.addTab(getPaneString(Panes.RESENDER), gui_resender.createPanel());
			tabbedpane.addTab(getPaneString(Panes.VULCHECKHELPER), gui_vulcheckhelper.createPanel());
			tabbedpane.addTab(getPaneString(Panes.BULKSENDER), gui_bulksender.createPanel());
			tabbedpane.addTab(getPaneString(Panes.EXTENSIONS), gui_extensions.createPanel());
			tabbedpane.addTab(getPaneString(Panes.OPTIONS), gui_option.createPanel());
			tabbedpane.addTab(getPaneString(Panes.LOG), gui_log.createPanel());

			getContentPane().add(tabbedpane, BorderLayout.CENTER);

			interceptModel = InterceptModel.getInstance();
			interceptModel.addPropertyChangeListener(this);
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

			errWithStackTrace(e);
			errWithStackTrace(e);
		}
	}

	private GUIHistory initProjectAndHistory() throws Exception {
		var chooser = new GUIProjectChooserDialog(this);
		var restore = chooser.chooseAndSetup();
		return restore ? GUIHistory.restoreLastInstance(this) : GUIHistory.getInstance(this);
	}

	private void setLookandFeel() throws Exception {
		if (PacketProxyUtility.getInstance().isUnix()) {

			System.setProperty("awt.useSystemAAFontSettings", "on");
			System.setProperty("swing.aatext", "true");
		}

		// FlatLaf Modern Light Theme (IntelliJ)
		try {
			FlatIntelliJLaf.setup();
		} catch (Exception e) {
			// Fallback to Nimbus if FlatLaf fails
			for (LookAndFeelInfo clInfo : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(clInfo.getName())) {
					UIManager.setLookAndFeel(clInfo.getClassName());
					break;
				}
			}
		}

		// フォント設定をデフォルトに復元（シンタックスハイライト機能による影響を防ぐため）
		FontManager.getInstance().restoreUIFont();
		FontManager.getInstance().restoreFont();

		UIManager.getLookAndFeelDefaults().put("defaultFont", FontManager.getInstance().getUIFont());
		// OptionPaneのロケール
		JOptionPane.setDefaultLocale(I18nString.getLocale());

		setIconForWindows();
		addShortcutForWindows();
		addShortcutForMac();
		addDockIconForMac();
	}

	/** Windowsにアイコンを表示する */
	private void setIconForWindows() throws Exception {
		if (!PacketProxyUtility.getInstance().isWindows()) {

			return;
		}
		ImageIcon icon = new ImageIcon(getClass().getResource("/gui/icon.png"));
		setIconImage(icon.getImage());
	}

	/** MacのDock上でにPacketProxyアイコンを表示する */
	private void addDockIconForMac() throws Exception {
		if (!PacketProxyUtility.getInstance().isMac()) {

			return;
		}
		ImageIcon icon = new ImageIcon(getClass().getResource("/gui/icon.png"));
		Taskbar.getTaskbar().setIconImage(icon.getImage());
	}

	/** JTextPane上でCommand+Cとかでコピペをできるようにする */
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
		registerTabShortcut(KeyEvent.VK_R, hotkey, im, am, Panes.RESENDER.ordinal());
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
						DefaultEditorKit.selectAllAction),};

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
		registerTabShortcut(KeyEvent.VK_R, hotkey, im, am, Panes.RESENDER.ordinal());
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

	/** Macでフルスクリーン表示できるようにする */
	private void enableFullScreenForMac(Window window) throws Exception {
		if (!PacketProxyUtility.getInstance().isMac()) {

			return;
		}
		getRootPane().putClientProperty("apple.awt.fullscreenable", true);
	}

	// Nimbusのバグでjava1.6系列ではsetForegroundAt, setBackgroundAtが効かない
	// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6939001
	private void setInterceptHighLight() {
		JLabel label = new JLabel(tabbedpane.getTitleAt(1));
		label.setForeground(new Color(255, 180, 0)); // Bright orange for dark theme
		tabbedpane.setTabComponentAt(1, label);
		tabbedpane.revalidate();
		tabbedpane.repaint();
	}

	private void setInterceptDownLight() {
		JLabel label = new JLabel(tabbedpane.getTitleAt(1));
		// Use default foreground color from Look and Feel
		label.setForeground(UIManager.getColor("TabbedPane.foreground"));
		tabbedpane.setTabComponentAt(1, label);
		tabbedpane.revalidate();
		tabbedpane.repaint();
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (interceptModel.getData() == null) {

			setInterceptDownLight();
		} else {

			setInterceptHighLight();
		}
	}
}
