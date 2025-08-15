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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import packetproxy.common.I18nString;
import packetproxy.model.Filter;
import packetproxy.model.Filters;

public class GUIFilterConfigEditDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Update"));
	private JTextField text_project_name = new JTextField();
	private JTextArea text_comment = new JTextArea();
	JComboBox<String> type_combo = new JComboBox<String>();
	private int height = 250;
	private int width = 700;
	private Filter filter;

	private JComponent label_and_object(String label_name, JComponent object) {
		return label_and_object(label_name, object, 2);
	}

	private JComponent label_and_object(String label_name, JComponent object, int height) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(150, label.getMaximumSize().height));
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * height));
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

	public void showDialog() {
		setModal(true);
		setVisible(true);
	}

	private JComponent createNameSetting() {
		return label_and_object(I18nString.get("Filter name:"), text_project_name);
	}

	private JComponent createCommentSetting() {
		return label_and_object(I18nString.get("Filter:"), text_comment, 5);
	}

	public GUIFilterConfigEditDialog(JFrame owner, Filter flt) throws Exception {
		super(owner);
		setTitle(I18nString.get("Setting"));
		this.filter = flt;
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		text_project_name.setText(flt.getName());
		text_comment.setLineWrap(true);
		text_comment.setWrapStyleWord(true);
		text_comment.setText(flt.getFilter());
		panel.add(createNameSetting());
		panel.add(createCommentSetting());

		panel.add(buttons());

		c.add(panel);

		button_cancel.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					Filter f = Filters.getInstance().query(filter.getId());
					f.setName(text_project_name.getText());
					f.setFilter(text_comment.getText());
					Filters.getInstance().update(f);
					dispose();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});
	}
}
