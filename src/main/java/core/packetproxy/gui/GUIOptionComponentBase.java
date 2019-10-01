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
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import packetproxy.model.Server;
import packetproxy.model.Servers;
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
		return jcomponent;
	}

	protected JComponent createComponent(String[] menu, int[] menuWidth, MouseAdapter tableAction, ActionListener addAction, ActionListener editAction, ActionListener removeAction) {
		option_model = new OptionTableModel(menu, 0) {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) { return false; }
		};
		JPanel panel = new JPanel();
		table = new JTable(option_model);
		for (int i = 0; i < menu.length; i++) {
			table.getColumn(menu[i]).setPreferredWidth(menuWidth[i]);
		}
		int maxHeight = 150;
		((JComponent) table.getDefaultRenderer(Boolean.class)).setOpaque(true);
		table.addMouseListener(tableAction);
		CustomScrollPane scrollpane1 = new CustomScrollPane();
		scrollpane1.setViewportView(table);
		scrollpane1.setMaximumSize(new Dimension(800, maxHeight));
		scrollpane1.setBackground(Color.WHITE);

		panel.add(createTableButton(addAction, editAction, removeAction));
		panel.add(scrollpane1);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, maxHeight));
		panel.setBackground(Color.WHITE);
		return panel;
	}

	private JPanel createTableButton(ActionListener addAction, ActionListener editAction, ActionListener removeAction) {
		JPanel panel = new JPanel();

		JButton button_add = new JButton("Add");
		JButton button_edit = new JButton("Edit");
		JButton button_remove = new JButton("Remove");

		int height = button_add.getMaximumSize().height;

		button_add.setMaximumSize(new Dimension(100,height));
		button_edit.setMaximumSize(new Dimension(100,height));
		button_remove.setMaximumSize(new Dimension(100,height));

		panel.add(button_add);
		panel.add(button_edit);
		panel.add(button_remove);

		button_add.addActionListener(addAction);

		button_edit.addActionListener(editAction);

		button_remove.addActionListener(removeAction);

		panel.setMaximumSize(new Dimension(100, 150));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Color.WHITE);
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
