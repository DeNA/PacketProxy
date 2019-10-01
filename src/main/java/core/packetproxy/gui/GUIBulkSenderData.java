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

import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class GUIBulkSenderData {

	//private JFrame owner;
	private JPanel main_panel;
	private JTabbedPane data_pane;
	private GUIBulkSenderDataRaw raw_panel;
	private GUIHistoryBinary binary_panel;
	private byte[] showing_data;
	//private Type type;
	private Consumer<byte[]> onChanged;
	
	public enum Type { CLIENT, SERVER };
	
	public GUIBulkSenderData(JFrame owner, Type type, Consumer<byte[]> onChanged) {
		//this.owner = owner;
		this.showing_data = null;
		//this.type = type;
		this.onChanged = onChanged;
	}
	
	public JComponent createPanel() {
		main_panel = new JPanel();
		main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.Y_AXIS));

		raw_panel = new GUIBulkSenderDataRaw(data -> {
			onChanged.accept(data);
		});
		JComponent raw_text = raw_panel.createPanel();

		binary_panel = new GUIHistoryBinary();
		JComponent binary_text = binary_panel.createPanel();
		
		data_pane = new JTabbedPane();
		data_pane.addTab("Raw", raw_text);
		data_pane.addTab("Binary", binary_text);
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
	
	private void update() {
		if (showing_data == null)
			return;

		try {
			switch (data_pane.getSelectedIndex()) {
			case 0:
				raw_panel.setData(showing_data); break;
			case 1:
				binary_panel.setData(showing_data); break;
			default:
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void setData(byte[] data) {
		showing_data = data;
		update();
	}
}
