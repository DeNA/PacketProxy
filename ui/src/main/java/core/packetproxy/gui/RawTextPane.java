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
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Base64;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import packetproxy.VulCheckerManager;
import packetproxy.common.FontManager;
import packetproxy.common.I18nString;
import packetproxy.common.Range;
import packetproxy.common.Utils;
import packetproxy.model.Packet;
import packetproxy.util.CharSetUtility;
import packetproxy.util.PacketProxyUtility;
import packetproxy.vulchecker.VulChecker;

public class RawTextPane extends ExtendedTextPane {

	private CharSetUtility charSetUtility;

	public RawTextPane() throws Exception {
		charSetUtility = CharSetUtility.getInstance();

		int mask_key = ActionEvent.META_MASK;
		if (!PacketProxyUtility.getInstance().isMac()) {

			mask_key = ActionEvent.CTRL_MASK;
		}
		JPopupMenu menu = new JPopupMenu();

		addKeyListener(new KeyAdapter() {

			public void keyPressed(KeyEvent e) {
				int mask_key = KeyEvent.META_MASK;
				if (!PacketProxyUtility.getInstance().isMac()) {

					mask_key = KeyEvent.CTRL_MASK;
				}
				switch (e.getKeyCode()) {
					case KeyEvent.VK_Z :
						if ((Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() & e.getModifiers()) > 0) {
							/* Command key */

							if (e.isShiftDown()) {
								/* Ctrl-Shift-Z */

								if (undo_manager.canRedo())
									undo_manager.redo();
							} else {
								/* Ctrl-Z */

								if (undo_manager.canUndo())
									undo_manager.undo();
							}
							e.consume();
						}
						break;
					case KeyEvent.VK_Y :
						if ((Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() & e.getModifiers()) > 0) {
							/* Command key */

							if (undo_manager.canRedo())
								/* Ctrl-Y */ undo_manager.redo();
							e.consume();
						}
						break;
				}
			}
		});

		JMenuItem vulCheckers = new JMenuItem(I18nString.get("VulCheck Helpers"));
		vulCheckers.setFont(FontManager.getInstance().getUICaptionFont());
		vulCheckers.setEnabled(false);
		menu.add(vulCheckers);

		for (String vulCheckerName : VulCheckerManager.getInstance().getAllVulCheckers().keySet()) {

			VulChecker vulChecker = VulCheckerManager.getInstance().createInstance(vulCheckerName);
			JMenuItem vulCheckerItem = new JMenuItem(vulChecker.getName());
			vulCheckerItem.addActionListener(actionEvent -> {
				try {

					Range range = Range.of(getSelectionStart(), getSelectionEnd());
					Packet packet = GUIPacket.getInstance().getPacket();
					GUIVulCheckHelper.getInstance().addVulCheck(vulChecker, packet.getOneShotPacket(getData()), range);
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			});
			menu.add(vulCheckerItem);
		}

		menu.addSeparator();
		JMenuItem title_decoders = new JMenuItem(I18nString.get("Decoders"));
		title_decoders.setFont(FontManager.getInstance().getUICaptionFont());
		title_decoders.setEnabled(false);
		menu.add(title_decoders);

		JMenuItem url_decoder = new JMenuItem("URL Decoder");
		url_decoder.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String chasetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					byte[] data = new String(getData(), chasetName).substring(position_start, position_end).getBytes();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(URLDecoder.decode(new String(data), chasetName).getBytes());
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(url_decoder);

		JMenuItem base64_decoder = new JMenuItem("Base64 / Base64url Decoder");
		base64_decoder.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					byte[] data = new String(getData(), charSetUtility.getCharSet())
							.substring(position_start, position_end).getBytes();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					if (Utils.indexOf(data, 0, data.length, "_".getBytes()) >= 0
							|| Utils.indexOf(data, 0, data.length, "-".getBytes()) >= 0) { // base64url

						dlg.setData(Base64.getUrlDecoder().decode(data));
					} else { // base64

						dlg.setData(Base64.getDecoder().decode(data));
					}
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(base64_decoder);

		JMenuItem jwt_decoder = new JMenuItem("JWT Decoder");
		jwt_decoder.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String charSetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					byte[] data = new String(getData(), charSetName).substring(position_start, position_end).getBytes();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(Arrays.stream(new String(data, charSetName).split("\\."))
							.map(Base64.getUrlDecoder()::decode).reduce((a, b) -> {
								return ArrayUtils.addAll(ArrayUtils.addAll(a, ".".getBytes()), b);
							}).get());
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(jwt_decoder);

		JMenuItem unicode_unescaper = new JMenuItem("Unicode Unescaper");
		unicode_unescaper.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String charSetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					byte[] data = new String(getData(), charSetName).substring(position_start, position_end).getBytes();
					String selection = new String(data, charSetName);
					String unescaped = StringEscapeUtils.unescapeJava(selection);
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(unescaped.getBytes());
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(unicode_unescaper);

		menu.addSeparator();
		JMenuItem title_encoders = new JMenuItem(I18nString.get("Encoders"));
		title_encoders.setFont(FontManager.getInstance().getUICaptionFont());
		title_encoders.setEnabled(false);
		menu.add(title_encoders);

		JMenuItem url_encoder = new JMenuItem("URL Encoder");
		url_encoder.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String charSetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					byte[] data = new String(getData(), charSetName).substring(position_start, position_end).getBytes();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(URLEncoder.encode(new String(data), charSetName).getBytes());
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(url_encoder);

		JMenuItem base64_encoder = new JMenuItem("Base64 Encoder");
		base64_encoder.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String charSetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					byte[] data = new String(getData(), charSetName).substring(position_start, position_end).getBytes();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(Base64.getEncoder().encode(data));
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(base64_encoder);

		JMenuItem base64url_encoder = new JMenuItem("Base64url Encoder");
		base64url_encoder.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String charSetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					byte[] data = new String(getData(), charSetName).substring(position_start, position_end).getBytes();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(Base64.getUrlEncoder().encode(data));
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(base64url_encoder);

		JMenuItem jwt_encoder = new JMenuItem("JWT Encoder");
		jwt_encoder.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String charSetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					byte[] data = new String(getData(), charSetName).substring(position_start, position_end).getBytes();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(Arrays.stream(new String(data, charSetName).split("\\.")).map((a) -> {
						String b = "";
						if (a.charAt(0) == '{') {

							b = new String(Base64.getUrlEncoder().encode(a.getBytes()));
						} else {
							/* signature */

							b = new String(Base64.getUrlEncoder()
									.encode("12345678901234567890123456789012".getBytes())); /* return 32 bytes data */
						}
						return StringUtils.strip(b, "=").getBytes();
					}).reduce((a, b) -> {
						return ArrayUtils.addAll(ArrayUtils.addAll(a, ".".getBytes()), b);
					}).get());
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(jwt_encoder);

		JMenuItem unicode_escaper = new JMenuItem("Unicode Escaper");
		unicode_escaper.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					if (charSetUtility.isAuto()) {

						charSetUtility.setGuessedCharSet(getData());
					}
					String charSetName = charSetUtility.getCharSet();
					int position_start = getSelectionStart();
					int position_end = getSelectionEnd();
					String selection = new String(getData(), charSetName).substring(position_start, position_end);
					StringBuilder sb = new StringBuilder();
					for (char c : selection.toCharArray()) {

						sb.append(String.format("\\u%04X", (int) c));
					}
					String unicode = sb.toString();
					GUIDecoderDialog dlg = new GUIDecoderDialog();
					dlg.setData(unicode.getBytes());
					dlg.showDialog();
				} catch (Exception e) {

					errWithStackTrace(e);
				}
			}
		});
		menu.add(unicode_escaper);

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
	public void copy() {
		String selected = getSelectedText();
		if (selected == null || selected.isEmpty()) {
			super.copy();
			return;
		}

		String sanitized = stripTrailingNewlines(selected);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		StringSelection selection = new StringSelection(sanitized);
		clipboard.setContents(selection, selection);
	}

	@Override
	public void setEditable(boolean b) {
		super.setEditable(b);
		if (!b) {
			this.setBackground(Color.WHITE);
		}
	}

	public void setData(byte[] data) throws Exception {
		init_flg = true;
		fin_flg = true;
		init_count = 0;
		prev_text_panel = "";
		raw_data.reset(data);
		if (charSetUtility.isAuto()) {

			charSetUtility.setGuessedCharSet(getData());
		}
		String charSetName = charSetUtility.getCharSet();
		setText(new String(data, charSetName));
		undo_manager.discardAllEdits();
	}

	public byte[] getData() {
		// Logging.log(raw_data.toString());
		return raw_data.toByteArray();
	}

	/* バイナリデータだとデータが壊れるので要注意 */
	public String getText() {
		return new String(raw_data.toByteArray());
	}

	/* バイナリデータだとデータが壊れるので要注意 */
	public void setText(String text) {
		try {

			fin_flg = true;
			init_flg = true;
			init_count = 0;
			prev_text_panel = "";
			raw_data.reset(text.getBytes());
			super.setText(text);
			undo_manager.discardAllEdits();
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	private static String stripTrailingNewlines(String s) {
		int end = s.length();
		while (end > 0) {
			char c = s.charAt(end - 1);
			if (c == '\n' || c == '\r') {
				end--;
				continue;
			}
			break;
		}
		return end == s.length() ? s : s.substring(0, end);
	}
}
