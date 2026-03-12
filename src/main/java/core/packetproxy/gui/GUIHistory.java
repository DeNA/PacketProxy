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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
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

	private static final int COL_ID = 0;
	private static final int COL_SERVER_RESPONSE = 2;
	private static final int COL_LENGTH = 3;
	private static final int COL_MODIFIED = 10;
	private static final int COL_CONTENT_TYPE = 11;

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
	private PacketPairingService pairingService;

	private Color packetColorGreen = new Color(0x7f, 0xff, 0xd4);
	private Color packetColorBrown = new Color(0xd2, 0x69, 0x1e);
	private Color packetColorYellow = new Color(0xff, 0xd7, 0x00);

	private GUIHistory(boolean restore) throws Exception {
		packets = Packets.getInstance(restore);
		packets.addPropertyChangeListener(this);
		ResenderPackets.getInstance().initTable(restore);
		Filters.getInstance().addPropertyChangeListener(this);
		pairingService = new PacketPairingService();
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

				int id = (int) table.getValueAt(i, COL_ID);
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
		int buttonWidth = 35; // ボタンの横幅を広げる
		filterConfigAdd.setPreferredSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
		filterConfigAdd.setMaximumSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
		filterConfigAdd.setMinimumSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
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
		filterDropDown.setPreferredSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
		filterDropDown.setMaximumSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
		filterDropDown.setMinimumSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
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
		filterConfig.setPreferredSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
		filterConfig.setMaximumSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
		filterConfig.setMinimumSize(new Dimension(buttonWidth, gui_filter.getMaximumSize().height));
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
					// Logging.log(ids.toString());
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
					int packetId = (int) table.getValueAt(row, COL_ID);
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
		// Set header alignment to left with border and padding, while preserving sort
		// icons on the
		// right edge
		TableHeaderStyle.apply(table, columnNames.length);
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
						resolveAndShowPacket(packet, false);
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

			return (Integer) table.getValueAt(idx, COL_ID);
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
		if (value >= 0) {

			updateRequestOne(value);
			return;
		}

		int packetId = value * -1;
		Packet packet = packets.query(packetId);
		long groupId = packet.getGroup();
		boolean isResponse = packet.getDirection() == Packet.Direction.SERVER;
		int packetCount = countAndTrackPacket(packet);
		if (shouldUnmergeExisting(packetCount, groupId)) {
			unmergeExistingPairing(groupId);
		}
		if (shouldMergeResponse(groupId, isResponse)) {
			mergeResponseIntoRequestRow(packet, groupId, packetId, true);
		} else {
			addNewRowWithGroupTracking(packet, packetId, isResponse, groupId);
		}
	}

	private int countAndTrackPacket(Packet packet) {
		long groupId = packet.getGroup();
		if (groupId == 0) {
			return 0;
		}
		int packetCount = pairingService.incrementGroupPacketCount(groupId);
		if (packet.getDirection() == Packet.Direction.CLIENT) {
			pairingService.incrementGroupClientPacketCount(groupId);
		}
		return packetCount;
	}

	private boolean shouldMergeResponse(long groupId, boolean isResponse) {
		if (!isResponse || groupId == 0) {
			return false;
		}
		return pairingService.containsGroup(groupId) && !pairingService.hasResponse(groupId)
				&& pairingService.isGroupMergeable(groupId);
	}

	private boolean shouldUnmergeExisting(int packetCount, long groupId) {
		return packetCount == 3 && pairingService.containsGroup(groupId) && pairingService.hasResponse(groupId);
	}

	private byte[] getDisplayData(Packet packet) {
		if (packet.getDecodedData().length > 0) {
			return packet.getDecodedData();
		}
		return packet.getModifiedData();
	}

	private String resolveContentType(Packet requestPacket, Packet responsePacket) {
		String contentType = requestPacket.getContentType();
		if (contentType == null || contentType.isEmpty()) {
			contentType = responsePacket.getContentType();
		}
		return contentType;
	}

	private void mergeResponseIntoRequestRow(Packet responsePacket, long groupId, int responsePacketId,
			boolean refreshSelection) throws Exception {
		Integer rowIndex = pairingService.getRowForGroup(groupId);
		if (rowIndex == null) {
			return;
		}
		int requestPacketId = (Integer) tableModel.getValueAt(rowIndex, COL_ID);
		tableModel.setValueAt(responsePacket.getSummarizedResponse(), rowIndex, COL_SERVER_RESPONSE);
		int currentLength = (Integer) tableModel.getValueAt(rowIndex, COL_LENGTH);
		tableModel.setValueAt(currentLength + getDisplayData(responsePacket).length, rowIndex, COL_LENGTH);
		Packet requestPacket = packets.query(requestPacketId);
		tableModel.setValueAt(resolveContentType(requestPacket, responsePacket), rowIndex, COL_CONTENT_TYPE);
		boolean currentModified = (boolean) tableModel.getValueAt(rowIndex, COL_MODIFIED);
		tableModel.setValueAt(currentModified || responsePacket.getModified(), rowIndex, COL_MODIFIED);
		pairingService.markGroupHasResponse(groupId);
		pairingService.registerPairing(responsePacketId, requestPacketId);
		id_row.put(responsePacketId, rowIndex);
		if (refreshSelection && requestPacketId == getSelectedPacketId()) {
			resolveAndShowPacket(requestPacket, true);
		}
	}

	private void mergeResponseMappingOnly(int responsePacketId, long groupId) {
		Integer rowIndex = pairingService.getRowForGroup(groupId);
		if (rowIndex == null) {
			return;
		}
		int requestPacketId = (Integer) tableModel.getValueAt(rowIndex, COL_ID);
		pairingService.markGroupHasResponse(groupId);
		pairingService.registerPairing(responsePacketId, requestPacketId);
		id_row.put(responsePacketId, rowIndex);
	}

	private void addNewRowWithGroupTracking(Packet packet, int packetId, boolean isResponse, long groupId)
			throws Exception {
		tableModel.addRow(makeRowDataFromPacket(packet));
		int rowIndex = tableModel.getRowCount() - 1;
		id_row.put(packetId, rowIndex);
		if (!isResponse && groupId != 0) {
			pairingService.registerGroupRow(groupId, rowIndex);
		}
	}

	private void addNewAsyncPlaceholderRowWithGroupTracking(int packetId, boolean isResponse, long groupId) {
		tableModel.addRow(new Object[]{packetId, "Loading...", "Loading...", 0, "Loading...", "", "Loading...", "",
				"00:00:00 1900/01/01 Z", false, false, "", "", "", (long) -1});
		int rowIndex = tableModel.getRowCount() - 1;
		id_row.put(packetId, rowIndex);
		if (!isResponse && groupId != 0) {
			pairingService.registerGroupRow(groupId, rowIndex);
		}
	}

	private void trackRequestGroupIfNeeded(int packetId, long groupId) {
		if (groupId == 0) {
			return;
		}
		Integer rowIndex = id_row.get(packetId);
		if (rowIndex == null) {
			return;
		}
		pairingService.ensureGroupTracked(groupId, rowIndex);
	}

	private void resolveAndShowPacket(Packet packet, boolean forceRefresh) throws Exception {
		int responsePacketId = pairingService.getResponsePacketIdForRequest(packet.getId());
		if (responsePacketId != -1) {
			Packet responsePacket = packets.query(responsePacketId);
			gui_packet.setPackets(packet, responsePacket, forceRefresh);
			return;
		}
		if (pairingService.containsResponsePairing(packet.getId())) {
			int requestPacketId = pairingService.getRequestIdForResponse(packet.getId());
			Packet requestPacket = packets.query(requestPacketId);
			gui_packet.setPackets(requestPacket, packet, forceRefresh);
			return;
		}
		long groupId = packet.getGroup();
		boolean isStreaming = groupId != 0 && pairingService.isGroupStreaming(groupId);
		if (isStreaming || packet.getDirection() == Packet.Direction.SERVER) {
			gui_packet.setSinglePacket(packet, forceRefresh);
			return;
		}
		gui_packet.setPackets(packet, null, forceRefresh);
	}

	/**
	 * 既存のマージを解除する（ストリーミング通信で3つ目以降のパケットが来た場合）
	 *
	 * @param groupId
	 *            グループID
	 */
	private void unmergeExistingPairing(long groupId) throws Exception {
		Integer rowIndex = pairingService.getRowForGroup(groupId);
		if (rowIndex == null) {
			return;
		}

		int requestPacketId = (Integer) tableModel.getValueAt(rowIndex, COL_ID);

		// 以前マージされていたレスポンスパケットIDを取得してペアリングを解除
		int responsePacketId = pairingService.unregisterPairingByRequestId(requestPacketId);
		pairingService.unmergeGroup(groupId);

		if (responsePacketId == -1) {
			return;
		}

		// リクエスト行を元に戻す（Server Response列をクリア、Lengthを再計算）
		Packet requestPacket = packets.query(requestPacketId);
		byte[] requestData = getDisplayData(requestPacket);
		tableModel.setValueAt("", rowIndex, COL_SERVER_RESPONSE); // Server Response列をクリア
		tableModel.setValueAt(requestData.length, rowIndex, COL_LENGTH); // Length列を再計算

		// 以前マージされていたレスポンスパケット用の新しい行を追加
		Packet responsePacket = packets.query(responsePacketId);
		tableModel.addRow(makeRowDataFromPacket(responsePacket));
		int newRowIndex = tableModel.getRowCount() - 1;
		id_row.put(responsePacketId, newRowIndex);

		// 選択中のパケットだった場合は詳細表示を更新
		if (requestPacketId == getSelectedPacketId()) {
			resolveAndShowPacket(requestPacket, true);
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

						resolveAndShowPacket(packet, false);
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
		id_row.clear();
		pairingService.clear();

		for (Packet packet : packetList) {

			long groupId = packet.getGroup();
			boolean isResponse = packet.getDirection() == Packet.Direction.SERVER;
			int packetCount = countAndTrackPacket(packet);
			if (shouldUnmergeExisting(packetCount, groupId)) {
				unmergeExistingPairing(groupId);
			}
			if (shouldMergeResponse(groupId, isResponse)) {
				mergeResponseIntoRequestRow(packet, groupId, packet.getId(), false);
			} else {
				addNewRowWithGroupTracking(packet, packet.getId(), isResponse, groupId);
			}
		}
		update_packet_ids.clear();
	}

	public void updateAllAsync() throws Exception {
		List<Packet> packetList = packets.queryAllIdsAndColors();
		tableModel.setRowCount(0);
		colorManager.clear();
		id_row.clear();
		pairingService.clear();

		for (Packet packet : packetList) {

			int id = packet.getId();
			String color = packet.getColor();
			long groupId = packet.getGroup();
			boolean isResponse = packet.getDirection() == Packet.Direction.SERVER;
			int packetCount = countAndTrackPacket(packet);
			if (shouldUnmergeExisting(packetCount, groupId)) {
				unmergeExistingPairingInAsyncModel(groupId);
			}
			if (shouldMergeResponse(groupId, isResponse)) {
				mergeResponseMappingOnly(id, groupId);
			} else {
				addNewAsyncPlaceholderRowWithGroupTracking(id, isResponse, groupId);
			}

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

	/**
	 * updateAllAsync用のマージ解除処理。
	 *
	 * <p>
	 * updateAllAsyncはIDと色のみを先に読み込み、実データは後続のupdateOneで補完するため、
	 * ここではペアリング情報の解除と、マージされていたレスポンス用のプレースホルダ行追加のみを行う。
	 */
	private void unmergeExistingPairingInAsyncModel(long groupId) {
		Integer rowIndex = pairingService.getRowForGroup(groupId);
		if (rowIndex == null) {
			return;
		}

		int requestPacketId = (Integer) tableModel.getValueAt(rowIndex, COL_ID);

		int responsePacketId = pairingService.unregisterPairingByRequestId(requestPacketId);
		pairingService.unmergeGroup(groupId);

		if (responsePacketId == -1) {
			return;
		}

		// 以前マージされていたレスポンスパケット用のプレースホルダ行を追加（実データはupdateOneで更新される）
		tableModel.addRow(new Object[]{responsePacketId, "Loading...", "Loading...", 0, "Loading...", "", "Loading...",
				"", "00:00:00 1900/01/01 Z", false, false, "", "", "", (long) -1});
		int newRowIndex = tableModel.getRowCount() - 1;
		id_row.put(responsePacketId, newRowIndex);
	}

	private void updateOne(Packet packet) throws Exception {
		if (id_row == null || packet == null) {

			return;
		}

		int packetId = packet.getId();
		boolean isResponse = packet.getDirection() == Packet.Direction.SERVER;
		long groupId = packet.getGroup();

		if (!isResponse && groupId != 0) {
			trackRequestGroupIfNeeded(packetId, groupId);
		}

		// マージされたレスポンスパケットの場合、リクエスト行を更新
		if (isResponse && pairingService.containsResponsePairing(packetId)) {

			Integer row_index = id_row.get(packetId);
			if (row_index != null) {

				// Server Response列を更新
				tableModel.setValueAt(packet.getSummarizedResponse(), row_index, COL_SERVER_RESPONSE);
				// Length列を再計算
				int requestPacketId = pairingService.getRequestIdForResponse(packetId);
				Packet requestPacket = packets.query(requestPacketId);
				byte[] requestData = getDisplayData(requestPacket);
				byte[] responseData = getDisplayData(packet);
				tableModel.setValueAt(requestData.length + responseData.length, row_index, COL_LENGTH);
				// Type列を更新
				tableModel.setValueAt(resolveContentType(requestPacket, packet), row_index, COL_CONTENT_TYPE);
				// Modified列を更新（リクエストまたはレスポンスのどちらかが改ざんされていれば true）
				boolean currentModified = (boolean) tableModel.getValueAt(row_index, COL_MODIFIED);
				tableModel.setValueAt(currentModified || packet.getModified(), row_index, COL_MODIFIED);
			}
			return;
		}

		Integer row_index = id_row.get(packetId);
		if (row_index == null || row_index < 0 || row_index >= tableModel.getRowCount()) {
			return;
		}
		Object[] row_data = makeRowDataFromPacket(packet);

		// マージされたリクエスト行の場合、Server Response / Length はレスポンス側で更新するため保持する
		boolean isMergedRequestRow = pairingService.isMergedRow(packetId);

		for (int i = 0; i < columnNames.length; i++) {

			// マージされた行のServer Response列（2）とLength列（3）はスキップ
			if (isMergedRequestRow && (i == COL_SERVER_RESPONSE || i == COL_LENGTH)) {

				continue;
			}

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

	/**
	 * リクエストパケットIDに対応するレスポンスパケットIDを取得する マージされた行の場合のみ有効
	 *
	 * @param requestPacketId
	 *            リクエストパケットID
	 * @return レスポンスパケットID、存在しない場合は-1
	 */
	public int getResponsePacketIdForRequest(int requestPacketId) {
		return pairingService.getResponsePacketIdForRequest(requestPacketId);
	}

	/**
	 * 選択された行がマージされた行（リクエスト+レスポンス）かどうかを判定
	 *
	 * @return マージされた行の場合true
	 */
	public boolean isSelectedRowMerged() {
		int packetId = getSelectedPacketId();
		return pairingService.isMergedRow(packetId);
	}
}
