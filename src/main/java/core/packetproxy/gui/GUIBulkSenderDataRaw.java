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
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import packetproxy.util.SearchBox;

public class GUIBulkSenderDataRaw implements RawTextPane.DataChangedListener
{
	private RawTextPane raw_text;
	private SearchBox searchBox;
	private JComponent panel;
	private JScrollPane text_panel;
	private Consumer<byte[]> onChanged;

	public GUIBulkSenderDataRaw(Consumer<byte[]> onChanged) throws Exception {
		this.onChanged = onChanged;
		raw_text = new RawTextPane();
		raw_text.addDataChangedListener(this);
		searchBox = new SearchBox();
		searchBox.setBaseText(raw_text);

		text_panel = new JScrollPane(raw_text);
		panel = new JPanel(new BorderLayout());
		panel.add(text_panel, BorderLayout.CENTER);
		panel.add(searchBox, BorderLayout.SOUTH);
	}

	public JComponent createPanel() {
		return panel;
	}

	public void appendData(byte[] data) throws Exception {
		javax.swing.text.StyledDocument document = raw_text.getStyledDocument();
		document.insertString(document.getLength(), new String(data), null);
	}

	public void setData(byte[] data) throws Exception {
		setData(data, true);
	}

	private void setData(byte[] data, boolean trimming) throws Exception {
		raw_text.setData(data, trimming);
	}

	public byte[] getData() {
		return raw_text.getData();
	}

	@Override
	public void dataChanged(byte[] data) {
		searchBox.textChanged();
		onChanged.accept(data);
	}
}
