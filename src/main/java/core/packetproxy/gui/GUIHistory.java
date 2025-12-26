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

import static packetproxy.model.PropertyChangeEventType.DATABASE_MESSAGE;
import static packetproxy.model.PropertyChangeEventType.FILTERS;
import static packetproxy.model.PropertyChangeEventType.PACKETS;
import static packetproxy.util.Logging.errWithStackTrace;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import packetproxy.common.FilterTextParser;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.common.Utils;
import packetproxy.model.Database;
import packetproxy.model.Database.DatabaseMessage;
import packetproxy.model.Filters;
import packetproxy.model.OptionTableModel;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.model.ResenderPackets;

public class GUIHistory implements PropertyChangeListener {

	private static GUIHistory instance;
	private static JFrame owner;

	public static JFrame getOwner() {
		return owner;
	}

	public static GUIHistory getInstance(JFrame frame) throws Exception {
		owner = frame;
		GUIHistory history = getInstance();
		return history;
	}

	public static GUIHistory getInstance() throws Exception {
		if (instance == null) {

			instance = new GUIHistory(false);
		}
		return instance;
	}

	public static GUIHistory restoreLastInstance(JFrame frame) throws Exception {
		owner = frame;
		instance = new GUIHistory(true);
		return instance;
	}

	private String[] columnNames = {"#", "Client Request", "Server Response", "Length", "Client IP", "Client Port",
			"Server IP", "Server Port", "Time", "Resend", "Modified", "Type", "Encode", "ALPN", "Group"};
	private int[] columnWidth = {60, 550, 50, 80, 160, 80, 160, 80, 100, 30, 30, 100, 100, 50, 30};
	private JSplitPane split_panel;
	private JPanel main_panel;
	private OptionTableModel tableModel;
	private TableCustomColorManager colorManager;
	private JTable table;
	private Packets packets;
	private GUIPacket gui_packet;
	TableRowSorter<OptionTableModel> sorter;
	private HintTextField gui_filter;
	private int preferredPosition;
	private ExecutorService history_update_service;
	private HashSet<Integer> update_packet_ids;
	private Hashtable<Integer, Integer> id_row;
	private boolean dialogOnce = false;
	private GUIHistoryAutoScroll autoScroll;
	private JPopupMenu menu;

	private Color packetColorGreen = new Color(0x7f, 0xff, 0xd4);
	private Color packetColorBrown = new Color(0xd2, 0x69, 0x1e);
	private Color packetColorYellow = new Color(0xff, 0xd7, 0x00);

	private GUIHistory(boolean restore) throws Exception {
		packets = Packets.getInstance(restore);
		packets.addPropertyChangeListener(this);
		ResenderPackets.getInstance().initTable(restore);
		Filters.getInstance().addPropertyChangeListener(this);
		gui_packet = GUIPacket.getInstance();
		colorManager = new TableCustomColorManager();
		preferredPosition = 0;
		update_packet_ids = new HashSet<Integer>();
		id_row = new Hashtable<Integer, Integer>();
		autoScroll = new GUIHistoryAutoScroll();
	}

	public DefaultTableModel getTableModel() {
		return this.tableModel;
	}

	public void filter() {
		boolean result = sortByText((String) gui_filter.getText());
		if (result == true) {

			for (int i = 0; i < table.getRowCount(); i++) {

				int id = (int) table.getValueAt(i, 0);
				if (id == preferredPosition) {

					table.changeSelection(i, 0, false, false);
					int rowsVisible = table.getParent().getHeight() / table.getRowHeight() / 2;
					Rectangle cellRect = table.getCellRect(i + rowsVisible, 0, true);
					table.scrollRectToVisible(cellRect);
				}
			}
		}
	}

	private JComponent createFilterPanel() throws Exception {
		gui_filter = new HintTextField(
				I18nString.get("filter string... (ex: request == example.com && type == image)"));
		gui_filter.setMaximumSize(new Dimension(Short.MAX_VALUE, gui_filter.getMinimumSize().height));

		gui_filter.addKeyListener(new KeyAdapter() {

			public void keyPressed(KeyEvent e) {
				try {

					switch (e.getKeyCode()) {
						case KeyEvent.VK_ENTER :
							filter();
							break;
					}
				} catch (Exception e1) {

					// Nothing to do
				}
			}
		});

		gui_filter.addFocusListener(new FocusListener() {

			@Override
			public void focusLost(FocusEvent e) {
				filter();
			}

			@Override
			public void focusGained(FocusEvent e) {
			}
		});

		JButton filterConfigAdd = new JButton(new ImageIcon(getClass().getResource("/gui/plus.png")));
		filterConfigAdd.setMaximumSize(new Dimension(15, gui_filter.getMaximumSize().height));
		filterConfigAdd.setBackground(filterConfigAdd.getBackground());
		filterConfigAdd.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIFilterConfigAddDialog dlg = new GUIFilterConfigAddDialog(owner, gui_filter.getText());
					dlg.showDialog();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});

		JToggleButton filterDropDown = new JToggleButton(new ImageIcon(getClass().getResource("/gui/arrow.png")));
		filterDropDown.setMaximumSize(
				new Dimension(filterConfigAdd.getMaximumSize().width, gui_filter.getMaximumSize().height));
		filterDropDown.setBackground(filterDropDown.getBackground());
		filterDropDown.addMouseListener(new MouseAdapter() {

			GUIFilterDropDownList dlg = null;

			@Override
			public void mouseReleased(MouseEvent arg0) {
				try {

					if (dlg != null) {

						dlg.dispose();
						dlg = null;
						filterDropDown.setSelected(false);
					} else {

						int x = gui_filter.getLocationOnScreen().x;
						int y = gui_filter.getLocationOnScreen().y + gui_filter.getHeight();
						int width = gui_filter.getWidth();
						int height = 0;
						dlg = new GUIFilterDropDownList(owner, width, filter -> {
							try {

								gui_filter.setText(filter.getFilter());
								filterDropDown.setSelected(false);
								filter();
								dlg.dispose();
								dlg = null;
							} catch (Exception e1) {

								errWithStackTrace(e1);
							}
						});
						dlg.setBounds(x, y, width, height); // 仮サイズ
						height = dlg.showDialog();
						dlg.setBounds(x, y, width, height); // 正式なサイズ
						filterDropDown.setSelected(true);
					}
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});

		ImageIcon icon = new ImageIcon(getClass().getResource("/gui/config.png"));
		JButton filterConfig = new JButton(icon);
		filterConfig.setMaximumSize(
				new Dimension(filterConfigAdd.getMaximumSize().width, gui_filter.getMaximumSize().height));
		filterConfig.setBackground(filterConfig.getBackground());
		filterConfig.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					GUIFilterConfigDialog dlg = new GUIFilterConfigDialog(owner);
					dlg.showDialog();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});

		JPanel filter_panel = new JPanel();
		filter_panel.setLayout(new BoxLayout(filter_panel, BoxLayout.X_AXIS));
		filter_panel.add(gui_filter);
		filter_panel.add(filterDropDown);
		filter_panel.add(filterConfigAdd);
		filter_panel.add(filterConfig);

		return filter_panel;
	}

	private JMenuItem createMenuItem(String name, int key, KeyStroke hotkey, ActionListener l) {
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

	public JComponent createPanel() throws Exception {
		tableModel = new OptionTableModel(columnNames, 0) {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		tableModel.addTableModelListener(new TableModelListener() {

			@Override
			public void tableChanged(TableModelEvent e) {
				if (e.getType() == TableModelEvent.INSERT) {

					// List<Integer> ids = searchFromRequest("google");
					// System.out.println(ids.toString());
				}
			}
		});

		table = new JTable(tableModel) {

			private static final long serialVersionUID = 1L;

			@Override
			public Component prepareRenderer(TableCellRenderer tcr, int row, int column) {
				Component c = super.prepareRenderer(tcr, row, column);
				try {

					int[] selected_rows = table.getSelectedRows();
					boolean selected = false;
					boolean first_selected = false;
					if (selected_rows.length >= 2) {

						for (int i = 0; i < selected_rows.length; i++) {

							if (selected_rows[i] == row) {

								selected = true;
								first_selected = (table.getSelectedRow() == row);
								break;
							}
						}
					} else {

						selected = (table.getSelectedRow() == row);
						first_selected = selected;
					}
					int packetId = (int) table.getValueAt(row, 0);
					boolean modified = (boolean) table.getValueAt(row,
							table.getColumnModel().getColumnIndex("Modified"));
					boolean resend = (boolean) table.getValueAt(row, table.getColumnModel().getColumnIndex("Resend"));
					if (selected) {

						if (first_selected) {

							c.setForeground(new Color(0xff, 0xff, 0xff));
							c.setBackground(new Color(0x80, 0x80, 0xff));
						} else {

							c.setForeground(new Color(0xff, 0xff, 0xff));
							c.setBackground(new Color(0xc0, 0xc0, 0xff));
						}
					} else if (colorManager.contains(packetId)) {

						c.setForeground(new Color(0x00, 0x00, 0x00));
						c.setBackground(colorManager.getColor(packetId));
					} else if (resend) {

						c.setForeground(new Color(0x00, 0x00, 0x00));
						c.setBackground(new Color(0x87, 0xce, 0xfa));
					} else if (modified) {

						c.setForeground(new Color(0x00, 0x00, 0x00));
						c.setBackground(new Color(0xff, 0xc0, 0xcb));
					} else {

						c.setForeground(new Color(0x00, 0x00, 0x00));
						if (row % 2 == 0)
							c.setBackground(new Color(0xff, 0xff, 0xff));
						else
							c.setBackground(new Color(0xf0, 0xf0, 0xf0));
					}
				} catch (Exception e) {

					errWithStackTrace(e);
				}
				return c;
			}
		};
		table.setRowHeight(FontManager.getInstance().getUIFontHeight(table));
		for (int i = 0; i < columnNames.length; i++) {

			table.getColumn(columnNames[i]).setPreferredWidth(columnWidth[i]);
		}
		// Set header alignment to left with border and padding
		DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value,
					boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY),
						BorderFactory.createEmptyBorder(2, 5, 2, 5)  // top, left, bottom, right padding
				));
				return c;
			}
		};
		headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);
		for (int i = 0; i < columnNames.length; i++) {
			table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
		}
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);
		table.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				try {

					preferredPosition = getSelectedPacketId();
					packets.refresh();
				} catch (Exception e1) {

					// Nothing to do
				}
			}
		});
		sorter = new TableRowSorter<OptionTableModel>(tableModel);
		sorter.setSortsOnUpdates(true);
		sorter.toggleSortOrder(14); /* 14 is 'group' column */
		table.setRowSorter(sorter);

		GUIHistoryContextMenuFactory.Handles handles = GUIHistoryContextMenuFactory.build(this, owner, table,
				gui_packet, packets, colorManager, packetColorGreen, packetColorBrown, packetColorYellow);
		menu = handles.getMenu();
		JMenuItem send = handles.getSend();
		JMenuItem sendToResender = handles.getSendToResender();
		JMenuItem copy = handles.getCopy();
		JMenuItem copyAll = handles.getCopyAll();

		table.addKeyListener(new KeyAdapter() {

			public void keyPressed(KeyEvent e) {
				try {

					int mask_key = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
					int p;
					switch (e.getKeyCode()) {
						case KeyEvent.VK_J :
							p = table.getSelectedRow() + 1;
							p = p >= table.getRowCount() ? table.getRowCount() - 1 : p;
							table.changeSelection(p, 0, false, false);
							break;
						case KeyEvent.VK_K :
							p = table.getSelectedRow() - 1;
							p = p < 0 ? 0 : p;
							table.changeSelection(p, 0, false, false);
							break;
						case KeyEvent.VK_Y :
							if ((e.getModifiersEx() & mask_key) == mask_key) {

								copy.doClick();
								break;
							}
						case KeyEvent.VK_S :
							if ((e.getModifiersEx() & mask_key) == mask_key) {

								send.doClick();
							}
							break;
						case KeyEvent.VK_R :
							if ((e.getModifiersEx() & mask_key) == mask_key) {

								sendToResender.doClick();
							}
							break;
						case KeyEvent.VK_M :
							if ((e.getModifiersEx() & mask_key) == mask_key) {

								copyAll.doClick();
							}
					}
					preferredPosition = getSelectedPacketId();
				} catch (Exception e1) {

					// Nothing to do
				}
			}
		});

		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent event) {
				if (Utils.isWindows() && event.isPopupTrigger()) {

					menu.show(event.getComponent(), event.getX(), event.getY());
				}
				autoScroll.doDisable();
			}

			@Override
			public void mousePressed(MouseEvent event) {
				try {

					if (event.isPopupTrigger()) {

						menu.show(event.getComponent(), event.getX(), event.getY());
					}
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		table.addComponentListener(new ComponentAdapter() {

			public void componentResized(ComponentEvent e) {
				try {

					if (autoScroll.isEnabled()) {

						table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
						table.changeSelection(table.getRowCount() - 1, 0, false, false);
						int packetId = getSelectedPacketId();
						Packet packet = packets.query(packetId);
						int retryCount = 10;
						while (packet.getDecodedData() == null || packet.getDecodedData().length == 0) {

							if (retryCount-- <= 0) {

								break;
							}
							Thread.sleep(100);
							packet = packets.query(packetId);
						}
						gui_packet.setPacket(packet);
					}
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});

		history_update_service = Executors.newSingleThreadExecutor();

		JScrollPane scrollpane = new JScrollPane(table);
		scrollpane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollpane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, autoScroll);

		split_panel = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split_panel.add(scrollpane);
		split_panel.add(gui_packet.createPanel());
		split_panel.setDividerLocation(200);
		split_panel.setAlignmentX(Component.CENTER_ALIGNMENT);

		main_panel = new JPanel();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		main_panel.add(createFilterPanel());
		main_panel.add(split_panel);

		return main_panel;
	}

	public List<Integer> searchFromRequest(String searchWord) {
		List<Integer> ids = new ArrayList<Integer>();
		for (int i = 0; i < table.getRowCount(); i++) {

			String req = (String) tableModel.getValueAt(i, 1);
			if (req.matches(String.format(".*%s.*", searchWord))) {

				ids.add(i);
			}
		}
		return ids;
	}

	public int getSelectedPacketId() {
		int idx = table.getSelectedRow();
		if (0 <= idx && idx < table.getRowCount()) {

			return (Integer) table.getValueAt(idx, 0);
		} else {

			return 0;
		}
	}

	private void saveHistoryWithAlertDialog() {
		if (dialogOnce) {

			return;
		}
		dialogOnce = true;
		JOptionPane.showMessageDialog(owner, "データベースのサイズが上限値(2GB)を越えそうです。Historyを保存してください。", "Warning",
				JOptionPane.WARNING_MESSAGE);

		WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "sqlite3");
		filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {

			@Override
			public void onApproved(File file, String extension) {
				try {

					Database.getInstance().Save(file.getAbsolutePath());
					JOptionPane.showMessageDialog(null, "データを保存しました。");
					updateRequest(true);
				} catch (Exception e1) {

					errWithStackTrace(e1);
					JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
				}
				dialogOnce = false;
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
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (PACKETS.matches(evt)) {

			handlePacketsPropertyChange(evt);
		} else if (FILTERS.matches(evt)) {

			handleFiltersPropertyChange(evt);
		} else if (DATABASE_MESSAGE.matches(evt)) {

			handleDatabaseMessagePropertyChange(evt);
		}
	}

	private void handlePacketsPropertyChange(PropertyChangeEvent evt) {
		SwingUtilities.invokeLater(() -> {
			try {

				Object arg1 = evt.getNewValue();
				if (arg1 instanceof Boolean) {

					handleBooleanPacketValue((Boolean) arg1);
				} else if (arg1 instanceof Integer) {

					handleIntegerPacketValue((Integer) arg1);
				} else if (arg1 instanceof DatabaseMessage && (DatabaseMessage) arg1 == DatabaseMessage.RECONNECT) {

					updateAllAsync();
				} else {

					updateRequest(true);
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
	}

	private void handleBooleanPacketValue(boolean value) {
		if (value) {

			// sqlite3のファイル上限(2GB)回避(model/Packets.javaから通知)
			saveHistoryWithAlertDialog();
		}
	}

	private void handleIntegerPacketValue(int value) throws Exception {
		if (value < 0) {

			int positiveValue = value * -1;
			tableModel.addRow(makeRowDataFromPacket(packets.query(positiveValue)));
			id_row.put(positiveValue, tableModel.getRowCount() - 1);
		} else {

			updateRequestOne(value);
		}
	}

	private void handleFiltersPropertyChange(PropertyChangeEvent evt) {
		SwingUtilities.invokeLater(() -> {
			try {

				Object arg1 = evt.getNewValue();
				if (arg1 instanceof DatabaseMessage && (DatabaseMessage) arg1 == DatabaseMessage.RECONNECT) {

					updateAllAsync();
				} else {

					filter();
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
	}

	private void handleDatabaseMessagePropertyChange(PropertyChangeEvent evt) {
		SwingUtilities.invokeLater(() -> {
			try {

				Object arg1 = evt.getNewValue();
				if (arg1 instanceof DatabaseMessage && (DatabaseMessage) arg1 == DatabaseMessage.RECONNECT) {

					updateAllAsync();
				}
			} catch (Exception e) {

				errWithStackTrace(e);
			}
		});
	}

	public void updateRequestOne(int id) throws Exception {
		synchronized (update_packet_ids) {
			update_packet_ids.add(id);
		}
		updateRequest(false);
	}

	/**
	 * @throws Exception
	 */
	private void updateRequest(boolean cursor_update) throws Exception {
		int select_id = getSelectedPacketId();
		// SingleThreadExecutor経由で実行しているので、このSwingWorkerは直列で動く
		SwingWorker<Packet, Packet> worker = new SwingWorker<Packet, Packet>() {

			@Override
			protected Packet doInBackground() throws Exception {
				HashSet<Integer> update_targets = new HashSet<Integer>();
				synchronized (update_packet_ids) {
					update_targets = (HashSet<Integer>) update_packet_ids.clone();
					update_packet_ids.clear();
				}
				for (int id : update_targets) {

					Packet packet = packets.query(id);
					publish(packet);
				}
				return cursor_update ? packets.query(select_id) : null;
			}

			@Override
			protected void process(List<Packet> packets) {
				try {

					for (Packet packet : packets) {

						updateOne(packet);
					}
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}

			@Override
			protected void done() {
				try {

					Packet packet = get();
					if (packet != null) {

						gui_packet.setPacket(packet);
					}
					// sortByText(gui_filter.getText());
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		};
		history_update_service.submit(worker);
	}

	// 新規作成時などの初期化用
	// TODO SwingWorkerの終了を待たないとバグるのでそれを修正する必要があるはず（あまりしないので優先度は低い）
	public void updateAll() throws Exception {
		List<Packet> packetList = packets.queryAll();
		tableModel.setRowCount(0);
		for (Packet packet : packetList) {

			tableModel.addRow(makeRowDataFromPacket(packet));
			id_row.put(packet.getId(), tableModel.getRowCount() - 1);
		}
		update_packet_ids.clear();
	}

	public void updateAllAsync() throws Exception {
		List<Packet> packetList = packets.queryAllIdsAndColors();
		tableModel.setRowCount(0);
		colorManager.clear();
		for (Packet packet : packetList) {

			int id = packet.getId();
			String color = packet.getColor();

			tableModel.addRow(new Object[]{packet.getId(), "Loading...", "Loading...", 0, "Loading...", "",
					"Loading...", "", "00:00:00 1900/01/01 Z", false, false, "", "", "", (long) -1});
			id_row.put(id, tableModel.getRowCount() - 1);

			if (Objects.equals(color, "green")) {

				colorManager.add(id, packetColorGreen);
			} else if (Objects.equals(color, "brown")) {

				colorManager.add(id, packetColorBrown);
			} else if (Objects.equals(color, "yellow")) {

				colorManager.add(id, packetColorYellow);
			}
		}
		update_packet_ids.clear();

		new Thread(new Runnable() {

			GUIHistory history = null;
			int count = 0;

			@Override
			public void run() {
				long limit = 100;
				for (long i = count; i > 0; i -= limit) {

					try {

						long offset;
						if (i - limit < 0) {

							offset = 0;
							limit = i;
						} else {

							offset = i - limit;
						}
						List<Packet> packetList = history.packets.queryRange(offset, limit);
						for (Packet packet : packetList) {

							SwingUtilities.invokeLater(new Runnable() {

								GUIHistory history = null;
								Packet packet = null;

								public Runnable set(GUIHistory history, Packet packet) {
									this.history = history;
									this.packet = packet;
									return this;
								}

								public void run() {
									try {

										updateOne(packet);
									} catch (Exception e) {

										// TODO Auto-generated catch block
										errWithStackTrace(e);
									}
								}
							}.set(history, packet));
						}
					} catch (Exception e) {

						errWithStackTrace(e);
					}
				}
			}

			public Runnable set(GUIHistory history, int count) {
				this.history = history;
				this.count = count;
				return this;
			}
		}.set(this, packetList.size())).start();
	}

	private void updateOne(Packet packet) throws Exception {
		if (id_row == null || packet == null) {

			return;
		}
		Integer row_index = id_row.getOrDefault(packet.getId(), tableModel.getRowCount() - 1);
		Object[] row_data = makeRowDataFromPacket(packet);

		for (int i = 0; i < columnNames.length; i++) {

			if (row_data[i] == tableModel.getValueAt(row_index, i)) {

				continue;
			}
			tableModel.setValueAt(row_data[i], row_index, i);
		}
	}

	private Object[] makeRowDataFromPacket(Packet packet) throws Exception {
		String client_ip = (packet.getClientIP() == null) ? "" : packet.getClientIP();
		String client_port = (packet.getClientPort() == 0) ? "" : String.valueOf(packet.getClientPort());
		String server_ip = (packet.getServerIP() == null) ? "" : packet.getServerIP();
		String server_port = (packet.getServerPort() == 0) ? "" : String.valueOf(packet.getServerPort());

		byte[] data = null;
		if (packet.getDecodedData().length > 0) {

			data = packet.getDecodedData();
		} else {

			data = packet.getModifiedData();
		}

		int length = data.length;

		SimpleDateFormat date_format = new SimpleDateFormat("HH:mm:ss yyyy/MM/dd Z");
		return new Object[]{packet.getId(), packet.getSummarizedRequest(), packet.getSummarizedResponse(), length,
				client_ip, client_port, server_ip, server_port, date_format.format(packet.getDate()),
				packet.getResend(), packet.getModified(), packet.getContentType(), packet.getEncoder(),
				packet.getAlpn(), packet.getGroup()};
	}

	private boolean sortByText(String text) {
		if (text.isEmpty()) {

			sorter.setRowFilter(null);
			return true;
		}
		try {

			sorter.setRowFilter(FilterTextParser.parse(text));
			return true;
		} catch (ParseException e) {

			// // ignore
			// errWithStackTrace(e);
		} catch (NumberFormatException e) {

			// // ignore
			// errWithStackTrace(e);
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		return false;
	}

	public void resetCustomColoring() {
		colorManager.clear();
		table.repaint();
	}

	public void addCustomColoring(int packetId, Color color) {
		colorManager.add(packetId, color);
		table.repaint();
	}

	public void addCustomColoringToCursorPos(Color color) {
		addCustomColoring(getSelectedPacketId(), color);
	}

	public boolean containsColor() {
		return colorManager.contains(getSelectedPacketId());
	}

	public Color getColor() throws Exception {
		return colorManager.getColor(getSelectedPacketId());
	}

	public int getSelectedIndex() {
		return table.getSelectedRow();
	}

	public void addMenu(JMenuItem menuItem) {
		menu.add(menuItem);
	}

	public void removeMenu(JMenuItem menuItem) {
		menu.remove(menuItem);
	}
}
