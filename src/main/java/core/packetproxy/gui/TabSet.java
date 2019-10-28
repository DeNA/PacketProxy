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
import java.awt.BorderLayout;
import java.util.Observable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import packetproxy.util.PacketProxyUtility;
import packetproxy.util.SearchBox;

public class TabSet extends Observable {
	private JPanel basePanel;
	private JTabbedPane data_pane;
	private GUIHistoryRaw raw_panel;
	private JComponent raw_text;
	private GUIHistoryBinary binary_panel;
	private JComponent binary_text;
	private GUIJson json_panel;
	private JComponent json_text;
	private JButton copyButton = null;
	private JButton parentSend = null;
	private JButton parentSendRepeater = null;
	private byte[] data;

	//Options
	private SearchBox searchBox = null;

	public TabSet(boolean search, boolean copy) throws Exception {
		raw_panel = new GUIHistoryRaw();
		raw_panel.setParentTabs(this);
		raw_text = raw_panel.createPanel();

		binary_panel = new GUIHistoryBinary();
		binary_panel.setParentTabs(this);
		binary_text = binary_panel.createPanel();

		json_panel = new GUIJson();
		json_text = json_panel.createPanel();

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
		data = null;

		basePanel = new JPanel();
		basePanel.setLayout(new BorderLayout());
		basePanel.add(data_pane);
		if (search == true) {
			searchBox = new SearchBox();
			basePanel.add(searchBox, BorderLayout.SOUTH);
		}
		if (copy == true) {
			copyButton = new JButton("copy to clipboard");
			basePanel.add(copyButton);
		}
	}

	public JPanel getTabPanel() {
		return basePanel;
	}

	public GUIHistoryRaw getRaw() {
		return raw_panel;
	}

	public GUIHistoryBinary getBinary() {
		return binary_panel;
	}

	public GUIJson getJson() {
		return json_panel;
	}

	public int getSelectedIndex() {
		return data_pane.getSelectedIndex();
	}

	public byte[] getData() {
		if (data == null) {
			return new byte[]{};
		}
		switch (getSelectedIndex()) {
			case 0:
				return raw_panel.getData();
			case 1:
				return binary_panel.getData();
			case 2:
				return json_panel.getData();
			default:
				PacketProxyUtility.getInstance().packetProxyLogErr("Not effective index, though this returns raw_panel data in such case.");
				return raw_panel.getData();
		}
	}

	public void setData(byte[] data) {
		this.data = data;
		update();
	}

	public JButton getParentSend () {
		return parentSend;
	}

	public void setParentSend (JButton parentSend) {
		this.parentSend = parentSend;
	}

	public JButton getParentSendRepeater () {
		return parentSendRepeater;
	}

	public void setParentSendRepeater (JButton parentSendRepeater) {
		this.parentSendRepeater = parentSendRepeater;
	}

	private void update() {
		if (data == null) {
			return;
		}
		try {
			raw_panel.setData(data);
			binary_panel.setData(data);
			json_panel.setData(PacketProxyUtility.getInstance().prettyFormatJSONInRawData(data));
			if (searchBox == null) {
				return;
			}
			switch (getSelectedIndex()) {
				case 0:
					searchBox.setVisible(true);
					searchBox.setBaseText(raw_panel.getTextPane());
					break;
				case 1:
					searchBox.setVisible(false);
					//searchBox.setBaseText(binary_panel.getTextPane());
					break;
				case 2:
					searchBox.setVisible(true);
					searchBox.setBaseText(json_panel.getTextPane());
					break;
				default:
					PacketProxyUtility.getInstance().packetProxyLogErr("Not effective index, though this returns raw_panel data in such case.");
					break;
			}
			searchBox.textChanged();
		} catch (Exception e) {
			e.printStackTrace();
		}
		notifyObservers(getSelectedIndex());
	}
	@Override
	public void notifyObservers(Object arg) {
		setChanged();
		super.notifyObservers(arg);
		clearChanged();
	}
}
