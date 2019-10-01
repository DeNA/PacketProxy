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
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class GUIDiffDialogParent extends JDialog
{
	private static final long serialVersionUID = 1L;
	private int width;
	private int height;
	private JPanel main_panel;
	private JTabbedPane data_pane;
	private GUIDiffRaw raw_panel;
	private GUIDiffBinary binary_panel;
	private GUIDiffJson json_panel;

	public void update() throws Exception {
		try {
			switch (data_pane.getSelectedIndex()) {
				case 0:
					raw_panel.update(); break;
				case 1:
					binary_panel.update(); break;
				case 2:
					json_panel.update(); break;
				default:
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void showDialog() {
		try {
			update();
			setModal(true);
			setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public GUIDiffDialogParent(JFrame owner) throws Exception {
		super(owner);
		setTitle("Diff");

		Rectangle rect = owner.getBounds();
		width = rect.width - 100;
		height = rect.height - 100;
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - height/2, width, height); /* ド真ん中 */

		createPanel();
		Container c = getContentPane();
		c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
		c.add(main_panel);

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

	public JComponent createPanel() {
		main_panel = new JPanel();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));
		try {
			raw_panel = new GUIDiffRaw();
			binary_panel = new GUIDiffBinary();
			json_panel = new GUIDiffJson();
		}catch (Exception e){
			e.printStackTrace();
		}
		JComponent raw_text = raw_panel.createPanel();
		JComponent binary_text = binary_panel.createPanel();
		JComponent json_text = json_panel.createPanel();

		data_pane = new JTabbedPane();
		data_pane.addTab("Raw", raw_text);
		data_pane.addTab("Binary", binary_text);
		data_pane.addTab("Json", json_text);
		data_pane.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				try {
					update();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		main_panel.add(data_pane);

		return main_panel;
	}
}
