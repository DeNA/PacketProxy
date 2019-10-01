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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JTextPane;
import packetproxy.common.SelectedArea;

public class GUIJson extends GUIHistoryPanel implements RawTextPane.DataChangedListener
{
	private RawTextPane raw_text;
	@Override
	public JTextPane getTextPane() {
		return raw_text;
	}

	private JComponent panel;
	private JScrollPane text_panel;

	public GUIJson()
	{
		raw_text = new RawTextPane();
		raw_text.setEditable(false);
		raw_text.addDataChangedListener(this);

		text_panel = new JScrollPane(raw_text);
		panel = new JPanel(new BorderLayout());
		panel.add(text_panel, BorderLayout.CENTER);
	}
	public JComponent createPanel() {
		return panel;
	}

	public void appendData(byte[] data) throws Exception {
		javax.swing.text.StyledDocument document = raw_text.getStyledDocument();
		document.insertString(document.getLength(), new String(data), null);
	}
	@Override
	public void setData(byte[] data) throws Exception {
		setData(data, true);
	}
	private void setData(byte[] data, boolean trimming) throws Exception {
		raw_text.setData(data, trimming);
	}
	@Override
	public byte[] getData() {
		return raw_text.getData();
	}

	@Override
	public void dataChanged(byte[] data) {
		callDataChanged(data);
	}

	@Override
	public void setParentTabs(TabSet parentTabs) {
	}

	@Override
	public JButton getParentSend() {
		return null;
	}
}
