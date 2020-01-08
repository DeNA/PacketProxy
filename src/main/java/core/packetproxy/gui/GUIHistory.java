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
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
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

import org.apache.commons.io.FileUtils;

import packetproxy.common.FilterTextParser;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.common.Utils;
import packetproxy.controller.ResendController;
import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.model.Database;
import packetproxy.model.Database.DatabaseMessage;
import packetproxy.model.Filters;
import packetproxy.model.OptionTableModel;
import packetproxy.model.Packet;
import packetproxy.model.Packets;
import packetproxy.util.CharSetUtility;

public class GUIHistory implements Observer
{
	private static GUIHistory instance;
	private static JFrame owner;

	public static JFrame getOwner() {
		return owner;
	}

	public static GUIHistory getInstance(JFrame frame) throws Exception
	{
		owner = frame;
		GUIHistory history = getInstance();
		return history;
	}

	public static GUIHistory getInstance() throws Exception
	{
		if (instance == null) {
			instance = new GUIHistory(false);
		}
		return instance;
	}

	public static GUIHistory restoreLastInstance(JFrame frame) throws Exception
	{
		owner = frame;
		instance = new GUIHistory(true);
		return instance;
	}

	private String[] columnNames = { "#", "Client Request", "Server Response", "Length", "Client IP", "Client Port", "Server IP", "Server Port", "Time", "Resend", "Modified", "Type", "Encode", "ALPN", "Group"};
	private int[] columnWidth = { 60, 550, 50, 80, 160, 80, 160, 80, 100, 30, 30, 100, 100, 50, 30 };
	private JSplitPane split_panel;
	private JPanel main_panel;
	private OptionTableModel tableModel;
	private TableCustomColorManager colorManager;
	private JTable table;
	private Packets packets;
	private GUIPacket gui_packet;
	TableRowSorter<OptionTableModel> sorter;
	private HintTextField gui_filter;
	private int preferedPosition;
	private ExecutorService history_update_service;
	private HashSet<Integer> update_packet_ids;
	private Hashtable<Integer, Integer> id_row;
	private boolean dialogOnce = false;
	private GUIHistoryAutoScroll autoScroll;

	private GUIHistory(boolean restore) throws Exception {
		packets = Packets.getInstance(restore);
		packets.addObserver(this);
		Filters.getInstance().addObserver(this);
		gui_packet = GUIPacket.getInstance();
		colorManager = new TableCustomColorManager();
		preferedPosition = 0;
		update_packet_ids = new HashSet<Integer>();
		id_row = new Hashtable<Integer, Integer>();
		autoScroll = new GUIHistoryAutoScroll();
	}

	public DefaultTableModel getTableModel() {
		return this.tableModel;
	}

	public void filter() {
		boolean result = sortByText((String)gui_filter.getText());
		if (result == true) {
			for (int i = 0; i < table.getRowCount(); i++) {
				int id = (int)table.getValueAt(i, 0);
				if (id == preferedPosition) {
					table.changeSelection(i, 0, false, false);
					int rowsVisible = table.getParent().getHeight()/table.getRowHeight()/2;
					Rectangle cellRect = table.getCellRect(i+rowsVisible, 0, true);
					table.scrollRectToVisible(cellRect);
				}
			}
		}
	}

	private JComponent createFilterPanel() throws Exception {
		gui_filter = new HintTextField(I18nString.get("filter string... (ex: request == example.com && type == image)"));
		gui_filter.setMaximumSize(new Dimension(Short.MAX_VALUE, gui_filter.getMinimumSize().height));

		gui_filter.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				try {
					switch (e.getKeyCode()) {
						case KeyEvent.VK_ENTER:
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
			public void focusGained(FocusEvent e) {}
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
					e1.printStackTrace();
				}
			}
		});

		JToggleButton filterDropDown = new JToggleButton(new ImageIcon(getClass().getResource("/gui/arrow.png")));
		filterDropDown.setMaximumSize(new Dimension(filterConfigAdd.getMaximumSize().width, gui_filter.getMaximumSize().height));
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
								e1.getStackTrace();
							}
						});
						dlg.setBounds(x, y, width, height); // 仮サイズ
						height = dlg.showDialog();
						dlg.setBounds(x, y, width, height); // 正式なサイズ
						filterDropDown.setSelected(true);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		ImageIcon icon = new ImageIcon(getClass().getResource("/gui/config.png"));
		JButton filterConfig = new JButton(icon);
		filterConfig.setMaximumSize(new Dimension(filterConfigAdd.getMaximumSize().width, gui_filter.getMaximumSize().height));
		filterConfig.setBackground(filterConfig.getBackground());
		filterConfig.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					GUIFilterConfigDialog dlg = new GUIFilterConfigDialog(owner);
					dlg.showDialog();
				} catch (Exception e1) {
					e1.printStackTrace();
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
		JMenuItem out = new JMenuItem (name);
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
			public boolean isCellEditable(int row, int column) { return false; }
		};
		tableModel.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				if (e.getType() == TableModelEvent.INSERT) {
					//List<Integer> ids = searchFromRequest("google");
					//System.out.println(ids.toString());
				}
			}
		});

		table = new JTable(tableModel) {
			private static final long serialVersionUID = 1L;
			@Override
			public Component prepareRenderer(TableCellRenderer tcr, int row, int column) {
				Component c = super.prepareRenderer(tcr, row, column);
				try {
					boolean selected = (table.getSelectedRow() == row);
					int packetId = (int)table.getValueAt(row, 0);
					boolean modified = (boolean)table.getValueAt(row, table.getColumnModel().getColumnIndex("Modified"));
					boolean resend = (boolean)table.getValueAt(row, table.getColumnModel().getColumnIndex("Resend"));
					if (selected) {
						c.setForeground(new Color(0xff, 0xff, 0xff));
						c.setBackground(new Color(0x80, 0x80, 0xff));
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
					e.printStackTrace();
				}
				return c;
			}
		};
		table.setRowHeight(FontManager.getInstance().getUIFontHeight(table));
		for (int i = 0; i < columnNames.length; i++) {
			table.getColumn(columnNames[i]).setPreferredWidth(columnWidth[i]);
		}
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);
		table.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				try {
					preferedPosition = getSelectedPacketId();
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

		JPopupMenu menu = new JPopupMenu();

		JMenuItem send = createMenuItem ("send", KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.META_MASK), new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					int id = GUIHistory.getInstance().getSelectedPacketId();
					Packet packet = Packets.getInstance().query(id);
					byte[] data = GUIPacket.getInstance().getData();
					if (packet == null) {
						return;
					}
					ResendController.getInstance().resend(packet.getOneShotPacket(data));
					packet.setResend();
					Packets.getInstance().update(packet);
					GUIHistory.getInstance().updateRequestOne(GUIHistory.getInstance().getSelectedPacketId());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JMenuItem sendRepeater = createMenuItem ("send to Resender", KeyEvent.VK_R, KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.META_MASK), new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					Packet packet = gui_packet.getPacket();
					packet.setResend();
					Packets.getInstance().update(packet);
					GUIRepeater.getInstance().addRepeats(packet.getOneShotFromModifiedData());
					GUIHistory.getInstance().updateRequestOne(GUIHistory.getInstance().getSelectedPacketId());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JMenuItem copyAll = createMenuItem ("copy Method + URL + Body", KeyEvent.VK_M, KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.META_MASK), new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					Packet packet = gui_packet.getPacket();
					Http http = new Http(packet.getSentData());
					CharSetUtility charsetutil = CharSetUtility.getInstance();
					if(charsetutil.isAuto()){
						charsetutil.setGuessedCharSet(http.getBody());
					}
					String copyData = http.getMethod() + "\t" +
						http.getURL(packet.getServerPort()) + "\t" + 
						new String(http.getBody(), charsetutil.getCharSet());
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					StringSelection selection = new StringSelection(copyData);
					clipboard.setContents(selection, selection);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JMenuItem copy = createMenuItem ("copy URL", KeyEvent.VK_Y, KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.META_MASK), new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					int id = GUIHistory.getInstance().getSelectedPacketId();
					Packet packet = Packets.getInstance().query(id);
					Http http = new Http(packet.getSentData());
					String url = http.getURL(packet.getServerPort());
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					StringSelection selection = new StringSelection(url);
					clipboard.setContents(selection, selection);

				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});

		JMenuItem bulkSender = createMenuItem ("send to Bulk Sender", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					GUIBulkSender.getInstance().add(gui_packet.getPacket().getOneShotFromModifiedData(), gui_packet.getPacket().getId());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		JMenuItem saveAll = createMenuItem ("save all data to file", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "dat", "packet.dat");
				filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {
				   @Override
				   public void onApproved(File file, String extension) {
					   try {
						   byte[] data = gui_packet.getPacket().getReceivedData();
						   FileUtils.writeByteArrayToFile(file, data);
						   JOptionPane.showMessageDialog(owner, String.format("%sに保存しました！", file.getPath()));
					   } catch (Exception e1) {
						   e1.printStackTrace();
						   JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
					   }
					   dialogOnce = false;
				   }
				   @Override
				   public void onCanceled() {}

				   @Override
				   public void onError() {
					   JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
				   }
			   });
				filechooser.showSaveDialog();
			}
		});

		JMenuItem saveHttpBody = createMenuItem ("save HTTP body to file", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "dat", "body.dat");
				filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {
					@Override
					public void onApproved(File file, String extension) {
						try {
							Http http = new Http(gui_packet.getPacket().getDecodedData());
							byte[] data = http.getBody();
							FileUtils.writeByteArrayToFile(file, data);
							JOptionPane.showMessageDialog(owner, String.format("%sに保存しました！", file.getPath()));
						} catch (Exception e1) {
							e1.printStackTrace();
							JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
						}
						dialogOnce = false;
					}
					@Override
					public void onCanceled() {}

					@Override
					public void onError() {
						JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
					}
				});
				filechooser.showSaveDialog();
			}
		});

		JMenuItem addColorG = createMenuItem ("add color (green)", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					Color color = new Color(0x7f, 0xff, 0xd4);
					colorManager.add(getSelectedPacketId(), color);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		JMenuItem addColorB = createMenuItem ("add color (brown)", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					Color color = new Color(0xd2, 0x69, 0x1e);
					colorManager.add(getSelectedPacketId(), color);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		JMenuItem addColorY = createMenuItem ("add color (yellow)", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					Color color = new Color(0xff, 0xd7, 0x00);
					colorManager.add(getSelectedPacketId(), color);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		JMenuItem clearColor = createMenuItem ("clear color", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					colorManager.clear(getSelectedPacketId());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		JMenuItem delete_item = createMenuItem ("delete this item", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					Packet packet = gui_packet.getPacket();
					colorManager.clear(packet.getId());
					packets.delete(packet);
					updateAll();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		});

		JMenuItem delete_all = createMenuItem ("delete all items", -1, null, new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					for(int i=0;i<table.getRowCount();++i) {
						Integer id = (Integer) table.getValueAt(i, 0);
						colorManager.clear(id);
						packets.delete(packets.query(id));
					}
					updateAll();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		});

		/*
		   Copy HTTP request as curl command:
		   $ curl 'http://example.com' -X POST -H 'Cookie: hoge=huga; foo=bar' -H ...

TODO: support --data-binary
*/
		JMenuItem copyAsCurl = createMenuItem ("copy as curl", -1, null, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					Http http = new Http(gui_packet.getPacket().getDecodedData());
					List<HeaderField> headerFields = http.getHeader().getFields();
					ArrayList<String> commandList = new ArrayList<>();
					commandList.add("curl");

					// 'http://example.com'
					String url = http.getURL(gui_packet.getPacket().getServerPort());
					commandList.add(String.format("'%s'", url));

					// -X POST
					commandList.add("-X");
					commandList.add(http.getMethod());

					// -H 'Cookie: hoge=huga; foo=bar' -H ...
					for (HeaderField hf : headerFields) {
						commandList.add("-H");
						commandList.add(String.format("'%s: %s'", hf.getName(), hf.getValue()));
					}

					// --data 'id=hoge&password=huga'
					String body = new String(http.getBody());
					// if body is not empty
					if (body.trim().length() > 0) {
						commandList.add("--data");
						commandList.add(String.format("'%s'", body));
					}

					commandList.add("--compressed");
					StringSelection command = new StringSelection(String.join(" ", commandList));

					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(command, command);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		menu.add(send);
		menu.add(sendRepeater);
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
		menu.add(delete_item);
		menu.add(delete_all);

		table.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				try {
					int p;
					Packet packet;
					Http http;
					Clipboard clipboard;
					StringSelection selection;
					switch (e.getKeyCode()) {
						case KeyEvent.VK_J:
							p = table.getSelectedRow()+1;
							p = p >= table.getRowCount() ? table.getRowCount()-1 : p;
							table.changeSelection(p, 0, false, false);
							break;
						case KeyEvent.VK_K:
							p = table.getSelectedRow()-1;
							p = p < 0 ? 0 : p;
							table.changeSelection(p, 0, false, false);
							break;
						case KeyEvent.VK_Y:
							if ((e.getModifiers() & KeyEvent.META_MASK) == KeyEvent.META_MASK) {
								copy.doClick();
								break;
							}
						case KeyEvent.VK_S:
							if ((e.getModifiers() & KeyEvent.META_MASK) == KeyEvent.META_MASK) {
								send.doClick();
							}
							break;
						case KeyEvent.VK_R:
							if ((e.getModifiers() & KeyEvent.META_MASK) == KeyEvent.META_MASK) {
								sendRepeater.doClick();
							}
							break;
						case KeyEvent.VK_M:
							if ((e.getModifiers() & KeyEvent.META_MASK) == KeyEvent.META_MASK) {
								copyAll.doClick();
							}
					}
					preferedPosition = getSelectedPacketId();
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
					e.printStackTrace();
				}
			}
		});
		table.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				try {
					if (autoScroll.isEnabled()) {
						table.scrollRectToVisible(table.getCellRect(table.getRowCount()-1, 0, true));
						table.changeSelection(table.getRowCount()-1, 0, false, false);
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
					e1.printStackTrace();
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
			String req = (String)tableModel.getValueAt(i, 1);
			if (req.matches(String.format(".*%s.*", searchWord))) {
				ids.add(i);
			}
		}
		return ids;
	}

	public int getSelectedPacketId() {
		int idx = table.getSelectedRow();
		if (0 <= idx && idx < table.getRowCount()) {
			return (Integer)table.getValueAt(idx, 0);
		} else {
			return 0;
		}
	}

	private void saveHistoryWithAlertDialog(){
		if(dialogOnce){
			return;

		}
		dialogOnce = true;
		JOptionPane.showMessageDialog(owner,
				"データベースのサイズが上限値(2GB)を越えそうです。Historyを保存してください。",
				"Warning",
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
					e1.printStackTrace();
					JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
				}
				dialogOnce = false;
			}

			@Override
			public void onCanceled() {}

			@Override
			public void onError() {
				JOptionPane.showMessageDialog(null, "データの保存に失敗しました。");
			}
		});
		filechooser.showSaveDialog();
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					if (null!=arg1 && arg1.getClass() == DatabaseMessage.class && (DatabaseMessage)arg1 == DatabaseMessage.RECONNECT) {
						updateAllAsync();
						return;
					}
					if (arg0.getClass() == Filters.class) {
						return;
					}
					if (arg0.getClass() == Packets.class) {
						if(arg1 instanceof  Boolean) {
							if(true==(boolean)arg1){//sqlite3のファイル上限(2GB)回避(model/Packets.javaから通知)
								saveHistoryWithAlertDialog();
							}
						}
						if (arg1 instanceof Integer) {
							if ((int)arg1 < 0) {
								tableModel.addRow(makeRowDataFromPacket(packets.query((int)arg1 * -1)));
								id_row.put((int)arg1 * -1, tableModel.getRowCount() - 1);
								return;
							} else {
								//problematic func
								updateRequestOne((int)arg1);
							}
						} else {
							updateRequest(true);
						}
						return;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
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
					update_targets = (HashSet<Integer>)update_packet_ids.clone();
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
					e.printStackTrace();
				}
			}
			@Override
			protected void done() {
				try {
					Packet packet = get();
					if (packet != null) {
						gui_packet.setPacket(packet);
					}
					//sortByText(gui_filter.getText());
				} catch (Exception e) {
					e.printStackTrace();
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
		List<Packet> packetList = packets.queryAllIds();
		tableModel.setRowCount(0);
		for(Packet packet : packetList) {
			tableModel.addRow(new Object[] {
				packet.getId(),"Loading...","Loading...",0,"Loading...","","Loading...","","00:00:00 1900/01/01 Z",false,false,"","","",(long)-1
			});
			id_row.put(packet.getId(), tableModel.getRowCount() - 1);
		}
		update_packet_ids.clear();

		new Thread(new Runnable() {
			GUIHistory history = null;
			int count = 0;
			@Override
			public void run() {
				long limit = 100;
				for(long i=count; i>0; i-=limit) {
					try {
						long offset;
						if (i - limit < 0) {
							offset = 0;
							limit = i;
						} else {
							offset = i - limit;
						}
						List<Packet> packetList = history.packets.queryRange(offset, limit);
						for(Packet packet : packetList) {
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
										e.printStackTrace();
									}
								}
							}.set(history, packet));
						}
					} catch (Exception e) {
						e.printStackTrace();
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
			if(row_data[i]==tableModel.getValueAt(row_index, i)){
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
		return new Object[] {
			packet.getId(),
				packet.getSummarizedRequest(),
				packet.getSummarizedResponse(),
				length,
				client_ip,
				client_port,
				server_ip,
				server_port,
				date_format.format(packet.getDate()),
				packet.getResend(),
				packet.getModified(),
				packet.getContentType(),
				packet.getEncoder(),
				packet.getAlpn(),
				packet.getGroup()
		};
	}

	private boolean sortByText(String text) {
		if (text.equals("")) {
			sorter.setRowFilter(null);
			return true;
		}
		try {
			sorter.setRowFilter(FilterTextParser.parse(text));
			return true;
		} catch (ParseException e) {
			//			// ignore
			//			e.printStackTrace();
		} catch (NumberFormatException e) {
			//			// ignore
			//			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
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
}
