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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import packetproxy.common.I18nString;
import packetproxy.model.CharSet;
import packetproxy.util.CharSetUtility;

public class GUIOptionCharSetDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private HintTextField text_charset = new HintTextField("(ex.) Shift_JIS");
	private String[] columns = new String[]{"", "CharSetName"};
	private CharSetsTableModel table_model;
	private TableRowSorter<CharSetsTableModel> sorter;
	private int height = 500;
	private int width = 500;
	private List<CharSet> charsets = new ArrayList<CharSet>();

	private Object[][] getTableDataWithAvailableCharsets() {
		List<String> availableCharSetList = CharSetUtility.getInstance().getAvailableCharSetList();
		Object a[][] = new Object[Charset.availableCharsets().size() - availableCharSetList.size()][2];
		int i = 0;
		for (String k : Charset.availableCharsets().keySet()) {

			if (availableCharSetList.contains(k)) {

				continue;
			}
			if (i >= a.length) {

				return a;
			}
			a[i++] = new Object[]{Boolean.FALSE, k};
		}
		return a;
	}

	private JComponent label_and_object(String label_name, JComponent object) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(150, label.getMaximumSize().height));
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
		panel.add(object);
		return panel;
	}

	private JComponent buttons() {
		JPanel panel_button = new JPanel();
		panel_button.setLayout(new BoxLayout(panel_button, BoxLayout.X_AXIS));
		panel_button.setMaximumSize(new Dimension(Short.MAX_VALUE, button_set.getMaximumSize().height));
		panel_button.add(button_cancel);
		panel_button.add(button_set);
		return panel_button;
	}

	private JScrollPane tableScrollPane() {
		table_model = new CharSetsTableModel(getTableDataWithAvailableCharsets(), columns);
		JTable table = new JTable(table_model);
		TableColumn col = table.getColumnModel().getColumn(0);
		col.setMinWidth(50);
		col.setMaxWidth(50);
		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				if (0 == table.getSelectedColumn()) {

					return;
				}
				table.setValueAt(!(Boolean) table.getValueAt(table.getSelectedRow(), 0), table.getSelectedRow(), 0);
			}
		});
		sorter = new TableRowSorter<CharSetsTableModel>(table_model);
		table.setRowSorter(sorter);
		JScrollPane jscrollPane = new JScrollPane(table);

		return jscrollPane;
	}

	public List<CharSet> showDialog() {
		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				button_cancel.requestFocusInWindow();
			}
		});
		setModal(true);
		setVisible(true);
		return charsets;
	}

	private JComponent createIpSetting() {
		return label_and_object("文字コード名:", text_charset);
	}

	public GUIOptionCharSetDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle("設定");
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - width / 2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(createIpSetting());

		panel.add(tableScrollPane());

		panel.add(buttons());

		c.add(panel);

		text_charset.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent e) {
				super.keyReleased(e);
				RowFilter<DefaultTableModel, Object> filter = null;
				try {

					filter = RowFilter.regexFilter(String.format("(?i)%s", text_charset.getText(), 1));
				} catch (Exception e1) {

					e1.printStackTrace();
				}
				sorter.setRowFilter(filter);
			}
		});

		button_cancel.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				charsets.clear();
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				charsets = table_model.getCheckedValues();
				dispose();
			}
		});
	}
	class CharSetsTableModel extends DefaultTableModel {

		public CharSetsTableModel(Object[][] data, Object[] columnNames) {
			super(data, columnNames);
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			if (0 == column) {

				return true;
			}
			return false;
		}

		@Override
		public Class getColumnClass(int col) {
			return getValueAt(0, col).getClass();
		}

		public List<CharSet> getCheckedValues() {
			List<CharSet> ret = new ArrayList<CharSet>();
			for (int i = 0; i < dataVector.size(); i++) {

				Vector a = (Vector) dataVector.get(i);
				if (null == a) {

					continue;
				}
				if (null == a.get(0)) {

					continue;
				}
				if (null == a.get(1)) {

					continue;
				}
				if ((Boolean) a.get(0)) {

					ret.add(new CharSet((String) a.get(1)));
				}
			}
			return ret;
		}

	}
}
