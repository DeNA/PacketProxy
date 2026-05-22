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

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Binary;
import packetproxy.common.FontManager;
import packetproxy.common.StringUtils;

public class GUIHistoryBinary extends GUIHistoryPanel implements BinaryTextPane.DataChangedListener {

	private final int TRIMMING_SIZE = 100000;
	private final int DEFAULT_SHOW_SIZE = 2000;
	private BinaryTextPane binary_text;

	@Override
	public JTextPane getTextPane() {
		return binary_text;
	}

	private boolean show_all;
	private JComponent box_panel;
	private JComponent panel;
	private BinaryTextPane hex_text;
	private JTextPane ascii_text;
	private JTextField search_text;
	private TabSet parentTabs = null;

	private byte[] data;

	public GUIHistoryBinary() throws Exception {
		hex_text = new BinaryTextPane();
		hex_text.setParentHistory(this);
		hex_text.addDataChangedListener(this);
		// hex_text.setLineWrap(true);
		hex_text.setFont(FontManager.getInstance().getFont());
		hex_text.addMouseListener(new MouseListener() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (show_all) {

					return;
				}
				setData(data, false);
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
			}

			public void mouseReleased(MouseEvent e) {
				int position_start = hex_text.getSelectionStart();
				int position_end = hex_text.getSelectionEnd();
				if (position_start == position_end) {

					return;
				}
				coloringSearchBinary();
				highlightFromHex(position_start, position_end, java.awt.Color.CYAN);
			}
		});
		hex_text.addKeyListener(new KeyListener() {

			@Override
			public void keyPressed(KeyEvent arg0) {
			}

			@Override
			public void keyReleased(KeyEvent arg0) {
				String str = hex_text.getText();
				try {

					int a = hex_text.getCaretPosition();
					Binary b = new Binary(new Binary.HexString(str));
					if (Arrays.equals(data, b.toByteArray())) {

						return;
					} // 文字列の中身が変化してない場合は戻る
					data = b.toByteArray();
					hex_text.setText(b.toHexString(16).toString());
					ascii_text.setText(b.toAsciiString(16).toString());
					hex_text.setCaretPosition(a);
					coloringSearchBinary();
					callDataChanged(data);
				} catch (IllegalArgumentException e) {

				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}

			@Override
			public void keyTyped(KeyEvent arg0) {
			}
		});
		JScrollPane scrollpane3 = new JScrollPane(hex_text);

		ascii_text = new JTextPane();
		// ascii_text.setLineWrap(true);
		ascii_text.setFont(FontManager.getInstance().getFont());
		ascii_text.addMouseListener(new MouseListener() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (show_all) {

					return;
				}
				setData(data, false);
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
			}

			public void mouseReleased(MouseEvent e) {
				int position_start = ascii_text.getSelectionStart();
				int position_end = ascii_text.getSelectionEnd();
				if (position_start == position_end) {

					return;
				}
				coloringSearchBinary();
				highlightFromAscii(position_start, position_end, java.awt.Color.CYAN);
			}
		});
		JScrollPane scrollpane4 = new JScrollPane(ascii_text);
		scrollpane4.getVerticalScrollBar().setModel(scrollpane3.getVerticalScrollBar().getModel()); // 縦方向のスクロールをhex側と同期させる

		search_text = new JTextField();
		search_text.setFont(FontManager.getInstance().getFont());
		search_text.addKeyListener(new KeyListener() {

			@Override
			public void keyReleased(KeyEvent arg0) {
				// テキストの色変更
				coloringSearchBinary();
			}

			@Override
			public void keyTyped(KeyEvent arg0) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});

		box_panel = new JPanel();
		box_panel.add(scrollpane3);
		box_panel.add(scrollpane4);
		box_panel.setLayout(new BoxLayout(box_panel, BoxLayout.X_AXIS));
		panel = new JPanel(new BorderLayout());
		panel.add(box_panel, BorderLayout.CENTER);
		panel.add(search_text, BorderLayout.SOUTH);
	}

	public JComponent createPanel() {
		return panel;
	}

	@Override
	public void setData(byte[] data) {
		setData(data, true);
	}

	private void setData(byte[] data, boolean trimming) {
		try {

			hex_text.setFont(FontManager.getInstance().getFont());
			ascii_text.setFont(FontManager.getInstance().getFont());
			search_text.setFont(FontManager.getInstance().getFont());
			hex_text.setData(data, false);
			this.data = data;
			// データが多いと遅いので長いデータをトリミングする
			if (trimming && data.length > TRIMMING_SIZE) {

				show_all = false;
				byte[] head = ArrayUtils.subarray(data, 0, DEFAULT_SHOW_SIZE);
				Binary b = new Binary(head);
				hex_text.setText(
						"********************\n  This data is too long.\n  If you want to show all message, please click this panel\n********************\n\n\n\n\n\n"
								+ b.toHexString(16).toString());
				ascii_text.setText(
						"********************\n  This data is too long.\n  If you want to show all message, please click this panel\n********************\n\n\n\n\n\n"
								+ b.toAsciiString(16).toString());
			} else {

				show_all = true;
				Binary b = new Binary(data);
				hex_text.setText(b.toHexString(16).toString());
				ascii_text.setText(b.toAsciiString(16).toString());
			}
			hex_text.setCaretPosition(0);
			ascii_text.setCaretPosition(0);
			coloringSearchBinary();
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	@Override
	public byte[] getData() {
		return data;
	}

	/**
	 * @return search_stringを見つけた回数
	 */
	public int coloringSearchBinary() {
		String str = hex_text.getText();
		if (str.length() > 1000000) {

			// Logging.err("[Warning] coloringSearchBinary: too long string. Skipping
			// Highlight");
			return -1;
		}

		// 検索用のバイト列を構築
		String search_string = search_text.getText();
		search_string = search_string.replaceAll(" ", "");
		try {

			byte[] search_bytes = StringUtils.hexToByte(search_string.getBytes());
			return coloringSearchBinary(search_bytes);
		} catch (Exception e) {

			// errWithStackTrace(e);
			byte[] search_bytes = search_text.getText().getBytes();
			return coloringSearchBinary(search_bytes);
		}
	}

	private int coloringSearchBinary(byte[] search_bytes) {
		String str = hex_text.getText();
		// 色を元に戻す
		resetHighlight();
		if (str.isEmpty() || search_bytes.length == 0) {

			return 0;
		}

		// 色を変える
		int cnt = 0;
		int start = 0;
		while ((start = StringUtils.binaryFind(data, search_bytes, start)) >= 0) {

			cnt++;
			int end = start + search_bytes.length;
			highlightFromAscii(start + start / 16, end + end / 16, java.awt.Color.yellow);
			start += search_bytes.length;
		}
		return cnt;
	}

	void resetHighlight() {
		javax.swing.text.MutableAttributeSet attributes = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setBackground(attributes, java.awt.Color.white);
		javax.swing.text.StyledDocument hex_document = hex_text.getStyledDocument();
		hex_document.setCharacterAttributes(0, hex_text.getText().length(), attributes, false);
		javax.swing.text.StyledDocument ascii_document = ascii_text.getStyledDocument();
		ascii_document.setCharacterAttributes(0, ascii_text.getText().length(), attributes, false);
	}

	void highlightFromHex(int hex_start, int hex_end, java.awt.Color color) {
		if (hex_start == hex_end) {

			return;
		}
		if (hex_end < hex_start) {

			int temp = hex_end;
			hex_end = hex_start;
			hex_start = temp;
		}
		javax.swing.text.MutableAttributeSet attributes = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setBackground(attributes, color);

		javax.swing.text.StyledDocument hex_document = hex_text.getStyledDocument();
		hex_document.setCharacterAttributes(hex_start, hex_end - hex_start, attributes, false);

		int ascii_start = toAsciiPos(hex_start);
		int ascii_end = toAsciiPos(hex_end) + 1;
		javax.swing.text.StyledDocument ascii_document = ascii_text.getStyledDocument();
		ascii_document.setCharacterAttributes(ascii_start, ascii_end - ascii_start, attributes, false);
	}

	void highlightFromAscii(int ascii_start, int ascii_end, java.awt.Color color) {
		if (ascii_start == ascii_end) {

			return;
		}
		if (ascii_end < ascii_start) {

			int temp = ascii_end;
			ascii_end = ascii_start;
			ascii_start = temp;
		}
		highlightFromHex(toHexPos(ascii_start), toHexPos(ascii_end) - 1, color);
	}

	// hexは1バイト毎にスペース, 両方とも16バイト毎に\n
	int toHexPos(int pos) {
		int y = pos / (16 + 1);
		int x = (pos - y * (16 + 1)) * 3;
		return y * (16 * 3 + 1) + x;
	}

	int toAsciiPos(int pos) {
		int y = pos / (16 * 3 + 1);
		int x = (pos - y * (16 * 3 + 1)) / 3;
		return y * (16 + 1) + x;
	}

	@Override
	public void setParentTabs(TabSet parentTabs) {
		this.parentTabs = parentTabs;
	}

	@Override
	public JButton getParentSend() {
		return parentTabs.getParentSend();
	}

	@Override
	public void dataChanged(byte[] data) {
	}
}
