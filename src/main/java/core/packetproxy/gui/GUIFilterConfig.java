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
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import packetproxy.common.FilterIO;
import packetproxy.common.I18nString;
import packetproxy.common.Utils;
import packetproxy.model.Filter;
import packetproxy.model.Filters;

public class GUIFilterConfig {

	private static final String defaultDir = System.getProperty("user.home");
	private JFrame owner;
	private ProjectTableModel project_model;
	private JTable table;
	private JComponent jcomponent;
	// private JTextField sort_field;
	// TableRowSorter<ProjectTableModel> sorter;

	public class ProjectTableModel extends DefaultTableModel {

		private static final long serialVersionUID = 1L;
		ProjectTableModel(String[] columnNames, int rowNum) {
			super(columnNames, rowNum);
		}

		@Override
		public Class<?> getColumnClass(int column) {
			return getValueAt(0, column).getClass();
		}
	}
	public GUIFilterConfig(JFrame owner) throws Exception {
		this.owner = owner;
		jcomponent = createComponent();
		updateImpl();
	}

	public JComponent createPanel() {
		return jcomponent;
	}

	private void tableFixedColumnWidth(JTable table, int[] menu_width, boolean[] fixed_map) {
		for (int index = 0; index < fixed_map.length; index++) {

			if (fixed_map[index]) {

				TableColumn col = table.getColumnModel().getColumn(index);
				col.setMinWidth(menu_width[index]);
				col.setMaxWidth(menu_width[index]);
			}
		}
	}

	private void tableAssignAlignment(JTable table, int[] align_map) {
		class HeaderRenderer implements TableCellRenderer {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				DefaultTableCellRenderer tcr = (DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
				JLabel label = (JLabel) tcr.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
						column);
				label.setHorizontalAlignment(align_map[column]);
				return label;
			}
		}
		HeaderRenderer hrenderer = new HeaderRenderer();
		for (int index = 0; index < align_map.length; index++) {

			DefaultTableCellRenderer crenderer = new DefaultTableCellRenderer();
			crenderer.setHorizontalAlignment(align_map[index]);
			table.getColumnModel().getColumn(index).setCellRenderer(crenderer);
			table.getColumnModel().getColumn(index).setHeaderRenderer(hrenderer);
		}
	}

	private JComponent createComponent() {
		String[] menu = {"#", I18nString.get("Filter name"), I18nString.get("Filter")};
		int[] menu_width = {40, 150, 610};
		boolean[] fixed_map = {true, false, false};
		int[] align_map = {JLabel.RIGHT, JLabel.LEFT, JLabel.LEFT};
		project_model = new ProjectTableModel(menu, 0) {

			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		JPanel panel = new JPanel();

		table = new JTable(project_model);
		tableFixedColumnWidth(table, menu_width, fixed_map);
		tableAssignAlignment(table, align_map);
		// sorter = new TableRowSorter<ProjectTableModel>(project_model);
		// table.setRowSorter(sorter);
		for (int i = 0; i < menu.length; i++) {

			table.getColumn(menu[i]).setPreferredWidth(menu_width[i]);
		}
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);

		JScrollPane scrollpane1 = new JScrollPane(table);
		scrollpane1.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		scrollpane1.setBackground(Color.WHITE);

		panel.add(createTableButton());
		panel.add(scrollpane1);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		panel.setBackground(Color.WHITE);
		panel.add(scrollpane1);

		// sort_field = new HintTextField("フィルタ文字列");
		// sort_field.getDocument().addDocumentListener(new DocumentListener() {
		// @Override
		// public void insertUpdate(DocumentEvent e) {
		// sortByText(sort_field.getText());
		// }
		// @Override
		// public void removeUpdate(DocumentEvent e) {
		// sortByText(sort_field.getText());
		// }
		// @Override
		// public void changedUpdate(DocumentEvent e) {
		// sortByText(sort_field.getText());
		// }
		// });
		// sort_field.setMaximumSize(new Dimension(Short.MAX_VALUE,
		// sort_field.getPreferredSize().height));

		JPanel vpanel = new JPanel();
		vpanel.setLayout(new BoxLayout(vpanel, BoxLayout.Y_AXIS));
		vpanel.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		// vpanel.add(sort_field);
		vpanel.add(panel);

		return vpanel;
	}

	private JPanel createTableButton() {
		JButton button_add = new JButton("Add");
		JButton button_update = new JButton("Edit");
		JButton button_remove = new JButton("Remove");
		JButton button_import = new JButton("Import");
		JButton button_export = new JButton("Export");

		int height = button_add.getMaximumSize().height;

		button_add.setMaximumSize(new Dimension(115, height));
		button_update.setMaximumSize(new Dimension(115, height));
		button_remove.setMaximumSize(new Dimension(115, height));
		button_import.setMaximumSize(new Dimension(115, height));
		button_export.setMaximumSize(new Dimension(115, height));

		JPanel panel = new JPanel();
		panel.add(button_add);
		panel.add(button_update);
		panel.add(button_remove);
		panel.add(button_import);
		panel.add(button_export);

		button_add.addMouseListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					GUIFilterConfigAddDialog dlg = new GUIFilterConfigAddDialog(owner);
					dlg.showDialog();
					updateImpl();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		button_update.addMouseListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					Filter filter = getSelectedTableContent();
					GUIFilterConfigEditDialog dlg = new GUIFilterConfigEditDialog(owner, filter);
					dlg.showDialog();
					updateImpl();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		button_remove.addMouseListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					Filter filter = getSelectedTableContent();
					int option = JOptionPane.showConfirmDialog(owner,
							String.format(I18nString.get("Are you sure you want to delete %s ?"), filter.getName()),
							I18nString.get("Delete filter"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
					if (option == JOptionPane.YES_OPTION) {

						Filters.getInstance().delete(filter);
						updateImpl();
					}
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		button_import.addMouseListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					JFileChooser filechooser = new JFileChooser();
					filechooser.setCurrentDirectory(new File(defaultDir));
					filechooser.setFileFilter(new FileNameExtensionFilter("*.json", "json"));
					filechooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
					int selected = filechooser.showOpenDialog(SwingUtilities.getRoot(owner));
					if (selected == JFileChooser.APPROVE_OPTION) {

						File file = filechooser.getSelectedFile();
						byte[] jbytes = Utils.readfile(file.getAbsolutePath());
						String json = new String(jbytes);
						FilterIO io = new FilterIO();
						io.setOptions(json);
						JOptionPane.showMessageDialog(null, I18nString.get("Config loaded successfully"));
						updateImpl();
					}
				} catch (Exception e1) {

					e1.printStackTrace();
					JOptionPane.showMessageDialog(null, I18nString.get("Config can't be loaded with error"));
				}
			}
		});

		button_export.addMouseListener(new java.awt.event.MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "json");
					filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {

						@Override
						public void onApproved(File file, String extension) {
							try {

								FilterIO io = new FilterIO();
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

		panel.setMaximumSize(new Dimension(115, Short.MAX_VALUE));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Color.WHITE);
		return panel;
	}

	private void updateImpl() throws Exception {
		clearTableContents();
		Filters.getInstance().queryAll().forEach(filter -> addTableContent(filter));
	}

	private void clearTableContents() {
		project_model.setRowCount(0);
	}

	private void addTableContent(Filter filter) {
		project_model.addRow(new Object[]{filter.getId(), filter.getName(), filter.getFilter()});
	}

	private Filter getSelectedTableContent() throws Exception {
		return getTableContent((int) table.getValueAt(table.getSelectedRow(), 0));
	}

	private Filter getTableContent(int id) throws Exception {
		return Filters.getInstance().query(id);
	}

	// private void sortByText(String text) {
	// if (text.equals("")) {
	// sorter.setRowFilter(null);
	// }
	// try {
	// List<RowFilter<Object,Object>> list = new ArrayList<>();
	// for (int i = 1; i < project_model.getColumnCount(); i++) {
	// list.add(RowFilter.regexFilter(text, i));
	// }
	// sorter.setRowFilter(RowFilter.orFilter(list));
	// } catch (Exception e) {
	// e.printStackTrace();
	// }
	// }
}
