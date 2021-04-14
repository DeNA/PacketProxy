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
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import packetproxy.common.I18nString;
import packetproxy.common.FontManager;
import packetproxy.model.OptionTableModel;

public abstract class GUIOptionComponentBase<T> implements Observer
{
	protected JFrame owner;
	protected OptionTableModel option_model;
	protected JTable table;
	protected JComponent jcomponent;

	public GUIOptionComponentBase(JFrame owner) {
		this.owner = owner;
	}

	public JComponent createPanel() {
		jcomponent.setAlignmentX(Component.LEFT_ALIGNMENT);
		return jcomponent;
	}

	protected JComponent createComponent(String[] menu, int[] menuWidth, MouseAdapter tableAction, ActionListener addAction, ActionListener editAction, ActionListener removeAction) throws Exception {
		option_model = new OptionTableModel(menu, 0) {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) { return false; }
		};

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		table = new JTable(option_model);
		for (int i = 0; i < menu.length; i++) {
			table.getColumn(menu[i]).setPreferredWidth(menuWidth[i]);
		}
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);
		table.addMouseListener(tableAction);
		table.setRowHeight(FontManager.getInstance().getUIFontHeight(table));

		CustomScrollPane scrollpane1 = new CustomScrollPane();
		scrollpane1.setViewportView(table);
		scrollpane1.setBackground(Color.WHITE);
		scrollpane1.setMinimumSize(new Dimension(800, 150));
		scrollpane1.setMaximumSize(new Dimension(800, 150));
		scrollpane1.setAlignmentY(Component.TOP_ALIGNMENT);

		panel.add(createTableButton(addAction, editAction, removeAction));
		panel.add(scrollpane1);
		panel.setBackground(Color.WHITE);
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, panel.getMinimumSize().height));
		return panel;
	}

	protected JComponent createComponentForServers(String[] menu, int[] menuWidth, MouseAdapter tableAction, ActionListener addAction, ActionListener editAction, ActionListener removeAction) throws Exception {
		option_model = new OptionTableModel(menu, 0) {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) { return false; }
		};
	
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		HintTextField filterText = new HintTextField(I18nString.get("Incremental Search for Host"));
		filterText.setMinimumSize(new Dimension(800, 30));
		filterText.setMaximumSize(new Dimension(800, 30));
		
		TableRowSorter<OptionTableModel> sorter = new TableRowSorter<OptionTableModel>(option_model);
		
		filterText.getDocument().addDocumentListener(new DocumentListener() {

		  @Override
		  public void insertUpdate(DocumentEvent e) {
			handleUpdate();
		  }

		  @Override
		  public void removeUpdate(DocumentEvent e) {        
			handleUpdate();
		  }

		  @Override
		  public void changedUpdate(DocumentEvent e) {
			handleUpdate();
		  }
			  
		  void handleUpdate() {
			RowFilter<OptionTableModel, Object> filter = null;
			try {
			  filter = RowFilter.regexFilter(filterText.getText(), 0);
			}
			catch(Exception ex) {
			}
			sorter.setRowFilter(filter);
		  }
		});

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

		JPanel sub_panel = new JPanel();
		sub_panel.setLayout(new BoxLayout(sub_panel, BoxLayout.Y_AXIS));
		sub_panel.add(scrollpane1);
		sub_panel.add(filterText);
		sub_panel.setAlignmentY(Component.TOP_ALIGNMENT);
		sub_panel.setBackground(Color.WHITE);
		
		panel.add(createTableButton(addAction, editAction, removeAction));
		panel.add(sub_panel);
		panel.setBackground(Color.WHITE);
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, panel.getMinimumSize().height));
		return panel;
	}


	private JPanel createTableButton(ActionListener addAction, ActionListener editAction, ActionListener removeAction) {
		JPanel panel = new JPanel();

		JButton button_add = new JButton("Add");
		JButton button_edit = new JButton("Edit");
		JButton button_remove = new JButton("Remove");

		int height = button_add.getMinimumSize().height;

		button_add.setMaximumSize(new Dimension(100,height));
		button_edit.setMaximumSize(new Dimension(100,height));
		button_remove.setMaximumSize(new Dimension(100,height));

		if(null!=addAction) {
			panel.add(button_add);
			button_add.addActionListener(addAction);
		}

		if(null!=editAction) {
			panel.add(button_edit);
			button_edit.addActionListener(editAction);
		}

		if(null!=removeAction) {
			panel.add(button_remove);
			button_remove.addActionListener(removeAction);
		}

		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Color.WHITE);
		panel.setMaximumSize(new Dimension(100, panel.getMinimumSize().height));
		panel.setAlignmentY(Component.TOP_ALIGNMENT);
		return panel;
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		updateImpl();
	}

	protected abstract void clearTableContents();
	protected abstract T getTableContent(int rowIndex);
	protected abstract T getSelectedTableContent();
	protected abstract void addTableContent(T t);
	protected abstract void updateTable(List<T> t);
	protected abstract void updateImpl();
}
