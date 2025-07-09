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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Base64;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.common.Utils;

@SuppressWarnings("serial")
public class BinaryTextPane extends ExtendedTextPane {
	private WrapEditorKit editor = new WrapEditorKit(new byte[]{});
	private byte[] data;

	public BinaryTextPane() throws Exception {
		setEditorKit(editor);
		setFont(FontManager.getInstance().getFont());

		JPopupMenu menu = new JPopupMenu();

		JMenuItem title_encoders = new JMenuItem(I18nString.get("Encoders"));
		title_encoders.setFont(FontManager.getInstance().getUICaptionFont());
		title_encoders.setEnabled(false);
		menu.add(title_encoders);

		JMenuItem base64_encoder = new JMenuItem("Base64 Encoder");
		base64_encoder.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					int position_start = (int) (getSelectionStart() / 3);
					int position_end = (int) (getSelectionEnd() / 3) + 1;
					byte[] data_org = getData();// new String(getData(), "UTF-8").substring(position_start,
												// position_end).getBytes();
					byte[] data = Arrays.copyOfRange(data_org, position_start, position_end);
					String copyData = new String(Base64.getEncoder().encode(data), "UTF-8");
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					StringSelection selection = new StringSelection(copyData);
					clipboard.setContents(selection, selection);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		menu.add(base64_encoder);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent event) {
				if (Utils.isWindows() && event.isPopupTrigger()) {
					menu.show(event.getComponent(), event.getX(), event.getY());
				}
			}
			@Override
			public void mousePressed(MouseEvent event) {
				if (event.isPopupTrigger()) {
					menu.show(event.getComponent(), event.getX(), event.getY());
				}
			}
		});
	}

	@Override
	public void setData(byte[] data) {
		this.data = data;
	}

	@Override
	public byte[] getData() {
		return data;
	}
}
