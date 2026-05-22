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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.EventListener;
import javax.swing.JButton;
import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoManager;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.BinaryBuffer;
import packetproxy.common.FontManager;
import packetproxy.common.Range;
import packetproxy.common.Utils;
import packetproxy.util.CharSetUtility;
import packetproxy.util.PacketProxyUtility;

abstract class ExtendedTextPane extends JTextPane {

	private static final long serialVersionUID = 3879881178060039018L;
	private static final int TEXT_TRIMMING_SIZE = 300000;
	private static final int BINARY_TRIMMING_SIZE = 10000; // バイナリデータの表示はテキストデータの表示に比べ大幅に時間がかかることを考慮して設定
	private static final int DEFAULT_SHOW_SIZE = 2000;
	private static final int FONT_SIZE = 12;
	private WrapEditorKit editor = new WrapEditorKit(new byte[]{});
	private GUIHistoryPanel parentHistory = null;
	public String prev_text_panel = "";
	public UndoManager undo_manager = new UndoManager();
	public BinaryBuffer raw_data = new BinaryBuffer();
	public int init_count = 0;
	private byte[] data;
	private boolean show_all;
	public boolean init_flg = false;
	public boolean fin_flg = false;
	protected EventListenerList listenerList = new EventListenerList();

	public ExtendedTextPane() throws Exception {
		setEditorKit(editor);
		setFont(FontManager.getInstance().getFont());
		getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				try {

					if (init_flg == true) {

						init_count += e.getLength();
						if (init_count == raw_data.getLengthInUTF8()) {

							init_flg = false;
							fin_flg = false;
							init_count = 0;
							prev_text_panel = e.getDocument().getText(0, e.getDocument().getLength());
						}
						return;
					}
					String str = e.getDocument().getText(e.getOffset(), e.getLength());
					prev_text_panel = e.getDocument().getText(0, e.getDocument().getLength());
					String before_string = prev_text_panel.substring(0, e.getOffset());
					raw_data.insert(before_string.getBytes().length, str.getBytes());
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				try {

					if (fin_flg == true) {

						fin_flg = false;
						return;
					}
					String before_removed_string = prev_text_panel.substring(0, e.getOffset());
					String removed_string = prev_text_panel.substring(e.getOffset(), e.getOffset() + e.getLength());
					prev_text_panel = e.getDocument().getText(0, e.getDocument().getLength());
					raw_data.remove(before_removed_string.getBytes().length, removed_string.getBytes().length);
					// Logging.log(String.format("remove: <%s> %d %d", removed_string,
					// removed_string.getBytes().length, e.getLength()));
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
			}
		});
		getDocument().addUndoableEditListener(new UndoableEditListener() {

			@Override
			public void undoableEditHappened(UndoableEditEvent e) {
				if (e.getEdit() instanceof DocumentEvent) {

					if (((DocumentEvent) e.getEdit()).getType() == DocumentEvent.EventType.CHANGE) {

						/* スタイルの編集の場合は無視 */
						return;
					}
				}
				undo_manager.addEdit(e.getEdit());
			}
		});
		addMouseListener(new MouseListener() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					if (show_all) {

						return;
					}
					setData(data, false);
				} catch (Exception e1) {

					errWithStackTrace(e1);
				}
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
			}

			public void mouseReleased(MouseEvent e) {
				showDecodedTooltipOnSelectedText();
				notifySelectedText();
			}
		});
		addKeyListener(new KeyListener() {

			@Override
			public void keyReleased(KeyEvent arg0) {
				// テキストの色変更
				if (Arrays.equals(data, getData())) {

					return;
				}
				data = getData(); // 文字列の中身が変化してない場合は戻る
				callDataChanged(data);
			}

			@Override
			public void keyTyped(KeyEvent arg0) {
				callDataChanged(getData());
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
	}

	public void setData(byte[] data, boolean trimming) throws Exception {
		setFont(FontManager.getInstance().getFont());
		this.data = data;
		// データが多いと遅いので長いデータをトリミングする
		if (trimming && (data.length > TEXT_TRIMMING_SIZE
				|| (PacketProxyUtility.getInstance().isBinaryData(data, BINARY_TRIMMING_SIZE)
						&& data.length > BINARY_TRIMMING_SIZE))) {

			show_all = false;
			byte[] head = ArrayUtils.subarray(data, 0, DEFAULT_SHOW_SIZE);
			CharSetUtility charSetUtility = CharSetUtility.getInstance();
			if (charSetUtility.isAuto()) {

				charSetUtility.setGuessedCharSet(getData());
			}
			String charSetName = charSetUtility.getCharSet();
			setText("********************\n  This data is too long.\n  If you want to show all message, please click this panel\n********************\n\n\n\n\n\n"
					+ new String(head, charSetName));
		} else {

			show_all = true;
			setData(data);
			callDataChanged(data);
		}
		setCaretPosition(0);
	}

	public void setParentHistory(GUIHistoryPanel parentHistory) {
		this.parentHistory = parentHistory;
	}

	public JButton getParentSend() {
		return parentHistory.getParentSend();
	}

	private void notifySelectedText() {
		int position_start = getSelectionStart();
		int position_end = getSelectionEnd();
		Range area = Range.of(position_start, position_end);
		if (area.getLength() == 0 || area.getLength() > 1000) {

			return;
		}
		byte[] request = Utils.getSelectedCharacters(data, area.getPositionStart(), area.getPositionEnd());
	}

	private void showDecodedTooltipOnSelectedText() {
		int position_start = getSelectionStart();
		int position_end = getSelectionEnd();
		Range area = Range.of(position_start, position_end);

		if (area.getLength() == 0) {

			return;
		}

		byte[] request = Utils.getSelectedCharacters(data, area.getPositionStart(), area.getPositionEnd());
		setToolTipText(new GUITooltipDecodeMessage(request).decodeMessage());
	}

	public interface DataChangedListener extends EventListener {

		void dataChanged(byte[] data);
	}

	public void addDataChangedListener(DataChangedListener listener) {
		listenerList.add(DataChangedListener.class, listener);
	}

	protected void callDataChanged(byte[] data) {
		for (DataChangedListener listener : listenerList.getListeners(DataChangedListener.class)) {

			listener.dataChanged(data);
		}
	}

	public abstract void setData(byte[] data) throws Exception;

	public abstract byte[] getData();
}
