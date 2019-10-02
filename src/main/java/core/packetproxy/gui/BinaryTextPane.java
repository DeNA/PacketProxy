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

import java.awt.Font;
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

import packetproxy.common.Utils;

public class BinaryTextPane extends ExtendedTextPane
{
	private String prev_text_panel = "";
	private WrapEditorKit editor = new WrapEditorKit(new byte[]{});
	private boolean init_flg = false;
	private int init_count = 0;
	private boolean fin_flg = false;
	private byte[] data;

	public BinaryTextPane() {
		setEditorKit(editor);
		setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		JPopupMenu menu = new JPopupMenu();

		JMenuItem title_encoders = new JMenuItem("エンコーダ");
		title_encoders.setFont(new Font("Arial", Font.BOLD, 12));
		title_encoders.setEnabled(false);
		menu.add(title_encoders);

		JMenuItem base64_encoder = new JMenuItem("Base64 Encoder");
		base64_encoder.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					int position_start = (int)(getSelectionStart()/3);
					int position_end   = (int)(getSelectionEnd()/3)+1;
					byte[] data_org = getData();//new String(getData(), "UTF-8").substring(position_start, position_end).getBytes();
					byte[] data = Arrays.copyOfRange(data_org,position_start,position_end);
					String copyData = new String(Base64.getEncoder().encode(data),"UTF-8");
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
	public byte[] getData()
	{
		return data;
	}
}
