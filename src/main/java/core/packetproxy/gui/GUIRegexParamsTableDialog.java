package packetproxy.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableRowSorter;
import packetproxy.common.FontManager;
import packetproxy.model.OptionTableModel;
import packetproxy.model.RegexParam;

public class GUIRegexParamsTableDialog extends JDialog {
	private JFrame owner;
	private int width;
	private int height = 300;
	private JPanel main_panel;
	private JTable table;
	private List<RegexParam> regexParams;
	private int basePacketId;
	private OptionTableModel option_model;
	private static String[] menu = {"Base Packet ID", "Param Name", "Regex to pickup"};
	private static int[] menuWidth = {100, 50, 200};

	public List<RegexParam> showDialog() {
		try {
			updateTable();
			setModal(true);
			setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this.regexParams;
	}

	public void updateTable() {
		option_model.setRowCount(0);
		for (RegexParam regex : regexParams) {
			option_model.addRow(new Object[]{regex.getPacketId(), regex.getName(), regex.getRegex()});
		}
	}

	public GUIRegexParamsTableDialog(JFrame owner, List<RegexParam> regexParams, int packetId) throws Exception {
		super(owner);
		this.owner = owner;

		this.regexParams = regexParams;
		this.basePacketId = packetId;

		setTitle("regex params");

		createPanel();
		updateTable();
		Container c = getContentPane();
		c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
		c.add(main_panel);

		Rectangle rect = owner.getBounds();
		width = rect.width - 100;
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				try {
					dispose();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public JComponent createPanel() throws Exception {
		MouseAdapter tableAction = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					int rowIndex = table.rowAtPoint(e.getPoint());
					table.setRowSelectionInterval(rowIndex, rowIndex);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};

		ActionListener addAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					GUIRegexParamDialog dlg = new GUIRegexParamDialog(owner);
					RegexParam regexParam = dlg.showDialog(new RegexParam(basePacketId, "", ""));
					if (regexParam != null) {
						regexParams.add(regexParam);
					}
					updateTable();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		ActionListener editAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					RegexParam oldRegexParam = getSelectedTableContent();
					GUIRegexParamDialog dlg = new GUIRegexParamDialog(owner);
					RegexParam newRegexParam = dlg.showDialog(oldRegexParam);
					if (newRegexParam != null) {
						regexParams.remove(oldRegexParam);
						regexParams.add(newRegexParam);
					}
					updateTable();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};
		ActionListener removeAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					regexParams.remove(getSelectedTableContent());
					updateTable();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		};

		main_panel = new JPanel();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.X_AXIS));

		option_model = new OptionTableModel(menu, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		TableRowSorter<OptionTableModel> sorter = new TableRowSorter<>(option_model);
		table = new JTable(option_model);
		for (int i = 0; i < menu.length; i++) {
			table.getColumn(menu[i]).setPreferredWidth(menuWidth[i]);
		}
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);
		table.addMouseListener(tableAction);
		table.setRowHeight(FontManager.getInstance().getUIFontHeight(table));

		table.setRowSorter(sorter);
		table.setModel(option_model);

		CustomScrollPane scrollpane1 = new CustomScrollPane();
		scrollpane1.setViewportView(table);
		scrollpane1.setBackground(Color.WHITE);
		scrollpane1.setMinimumSize(new Dimension(800, 150));
		scrollpane1.setMaximumSize(new Dimension(800, 150));
		scrollpane1.setAlignmentY(Component.TOP_ALIGNMENT);

		main_panel.add(createTableButton(addAction, editAction, removeAction));
		main_panel.add(scrollpane1);
		main_panel.setMaximumSize(new Dimension(Short.MAX_VALUE, main_panel.getMinimumSize().height));

		return main_panel;
	}

	private JPanel createTableButton(ActionListener addAction, ActionListener editAction, ActionListener removeAction) {
		JPanel panel = new JPanel();

		JButton button_add = new JButton("Add");
		JButton button_edit = new JButton("Edit");
		JButton button_remove = new JButton("Remove");

		int height = button_add.getMinimumSize().height;

		button_add.setMaximumSize(new Dimension(100, height));
		button_edit.setMaximumSize(new Dimension(100, height));
		button_remove.setMaximumSize(new Dimension(100, height));

		if (null != addAction) {
			panel.add(button_add);
			button_add.addActionListener(addAction);
		}
		if (null != editAction) {
			panel.add(button_edit);
			button_edit.addActionListener(editAction);
		}
		if (null != removeAction) {
			panel.add(button_remove);
			button_remove.addActionListener(removeAction);
		}

		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setMaximumSize(new Dimension(100, panel.getMinimumSize().height));
		panel.setAlignmentY(Component.TOP_ALIGNMENT);
		return panel;
	}

	protected RegexParam getSelectedTableContent() {
		return getTableContent(table.getSelectedRow());
	}

	protected RegexParam getTableContent(int rowIndex) {
		return regexParams.get(rowIndex);
	}
}
