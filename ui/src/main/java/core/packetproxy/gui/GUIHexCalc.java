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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.apache.commons.codec.binary.Hex;
import packetproxy.common.Binary;
import packetproxy.common.StringUtils;

public class GUIHexCalc {

	private JTextField int_before;
	private JTextField int_hex;
	private JTextField str_before;
	private JTextField str_hex;
	private JComboBox<String> endian_box;
	private JComponent int_panel;
	private JComponent str_panel;
	private JComponent main_panel;

	public GUIHexCalc() {
		createIntPanel();
		createStrPanel();
		main_panel = new JPanel();
		main_panel.setBackground(Color.WHITE);
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		main_panel.add(int_panel);
		main_panel.add(str_panel);
		main_panel.setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	public JComponent create() {
		return main_panel;
	}

	private void createIntPanel() {
		int_before = new JTextField();
		int_before.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent keyEvent) {
				int_to_hex_translation();
			}
		});
		int_hex = new JTextField();
		int_hex.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent keyEvent) {
				try {

					hex_to_int_translation();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});
		String[] combodata = {"Little Endian", "Big Endian"};
		endian_box = new JComboBox<String>(combodata);
		endian_box.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int_to_hex_translation();
			}
		});

		int_panel = new JPanel();
		int_panel.setBackground(Color.WHITE);
		int_panel.setLayout(new BoxLayout(int_panel, BoxLayout.X_AXIS));

		JLabel label = new JLabel("Integer <-> Hex");
		label.setHorizontalAlignment(JLabel.CENTER);
		label.setMaximumSize(new Dimension(100, label.getMaximumSize().height));
		int_panel.add(label);
		endian_box.setMaximumSize(new Dimension(100, label.getMaximumSize().height * 2));
		int_panel.add(endian_box);
		int_before.setMaximumSize(new Dimension(300, label.getMaximumSize().height * 2));
		int_panel.add(int_before);
		int_hex.setMaximumSize(new Dimension(400, label.getMaximumSize().height * 2));
		int_panel.add(int_hex);
		int_panel.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
	}

	private void createStrPanel() {
		str_before = new JTextField();
		str_before.addKeyListener(new KeyAdapter() {

			public void keyReleased(KeyEvent keyEvent) {
				str_to_hex_translation();
			}
		});
		str_hex = new JTextField();
		str_hex.addKeyListener(new KeyAdapter() {

			public void keyReleased(KeyEvent keyEvent) {
				try {

					hex_to_str_translation();
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}
		});

		str_panel = new JPanel();
		str_panel.setBackground(Color.WHITE);
		str_panel.setLayout(new BoxLayout(str_panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel("String <-> Hex");
		label.setHorizontalAlignment(JLabel.CENTER);
		label.setMaximumSize(new Dimension(100, label.getMaximumSize().height));
		str_panel.add(label);
		str_before.setMaximumSize(new Dimension(400, label.getMaximumSize().height * 2));
		str_panel.add(str_before);
		str_hex.setMaximumSize(new Dimension(400, label.getMaximumSize().height * 2));
		str_panel.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
		str_panel.add(str_hex);
	}

	private void str_to_hex_translation() {
		str_hex.setText(Hex.encodeHexString(str_before.getText().getBytes()));
	}

	private void int_to_hex_translation() {
		if (int_before.getText().isEmpty()) {

			int_hex.setText("");
			return;
		}
		Boolean endian = (endian_box.getSelectedItem().equals("Little Endian")) ? true : false;
		String r = StringUtils.intToHex(Integer.parseInt(int_before.getText()), endian);
		int_hex.setText(r);
	}

	private void hex_to_int_translation() throws Exception {
		if (int_hex.getText().isEmpty()) {

			int_before.setText("");
			return;
		}
		try {

			Boolean endian = (endian_box.getSelectedItem().equals("Little Endian")) ? true : false;
			Binary b = new Binary(new Binary.HexString(int_hex.getText()));
			int_before.setText(Integer.toString(b.toInt(endian)));
		} catch (IllegalArgumentException e) {

			/* Ignore case */
		}
	}

	private void hex_to_str_translation() throws Exception {
		try {

			str_before.setText(new Binary(new Binary.HexString(str_hex.getText())).toAsciiString().toString());
		} catch (IllegalArgumentException e) {

			/* Ignore case */
		}
	}
}
