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
package packetproxy.util;

import java.awt.Font;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import packetproxy.common.Utils;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.BoxLayout;

public class SearchBox extends JPanel
{
	private JTextPane baseText;
	public void setBaseText (JTextPane textPane) {
		baseText = textPane;
	}

	private JTextField search_text;
	public void setText(String text) {
		search_text.setText(text);
	}
	public String getText() {
		return search_text.getText();
	}

	private JLabel search_count;

	public SearchBox() {
		search_text = new JTextField();
		if (Utils.isWindows()) {
			search_text.setFont(new Font("ＭＳ ゴシック", Font.PLAIN, 13));
		} else {
			search_text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		}
		search_text.addKeyListener(new KeyListener() {
			private String prev_word = null;
			private int cur_pos = 0;
			@Override
			public void keyReleased(KeyEvent arg0) {
				// テキストの色変更
				int count = coloringSearchText();
				updateSearchCount(count);

				// returnキーの時は、そこへ移動してハイライト
				if (arg0.getKeyChar() == '\n') {
					String word = search_text.getText();
					if (word.equals(prev_word) == false) {
						prev_word = word;
						cur_pos = 0;
					}
					cur_pos = searchText(cur_pos+word.length(), word);
					if (cur_pos >= 0) {
						javax.swing.text.StyledDocument document = baseText.getStyledDocument();
						javax.swing.text.MutableAttributeSet attributes = new javax.swing.text.SimpleAttributeSet();
						attributes = new javax.swing.text.SimpleAttributeSet();
						javax.swing.text.StyleConstants.setBackground(attributes, java.awt.Color.magenta);
						document.setCharacterAttributes(cur_pos, word.length(), attributes, false);
						baseText.setCaretPosition(cur_pos);
					}
				}
			}
			@Override
			public void keyTyped(KeyEvent arg0) {
			}
			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
		search_count = new JLabel("Not found");
		search_count.setOpaque(true);
		search_count.setPreferredSize(new Dimension(75, 12));
		search_count.setHorizontalAlignment(JLabel.CENTER);

		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(search_text);
		add(search_count);
	}


	private int searchText(int start, String word) {
		String str = baseText.getText();
		int found = 0;
		if ((found = str.indexOf(word, start)) >= 0) {
			return found;
		}
		return -1;
	}

	public int coloringSearchText() {
		javax.swing.text.StyledDocument document = baseText.getStyledDocument();
		String str = baseText.getText();
		String search_string = search_text.getText();
		if (str.length() > 1000000) {
			//System.err.println("[Warning] coloringSearchText: too long string. Skipping Highlight");
			return -1;
		}

		// 色を元に戻す
		javax.swing.text.MutableAttributeSet attributes = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setBackground(attributes, java.awt.Color.white);
		document.setCharacterAttributes(0, str.length(), attributes, false);
		if (str.length() == 0 || search_string.length() == 0) { return 0; }

		// 色を変える
		int cnt = 0;
		int start = 0;
		while ((start = str.indexOf(search_string, start)) >= 0) {
			cnt++;
			javax.swing.text.StyleConstants.setBackground(attributes, java.awt.Color.yellow);
			document.setCharacterAttributes(start, search_string.length(), attributes, false);
			start += search_string.length();
		}
		return cnt;
	}

	/**
	 * TODO HTTPの構造を解釈して、明らかにパラメータではない所を除外する
	 */
	public void coloringHTTPText() {
		javax.swing.text.StyledDocument document = baseText.getStyledDocument();
		String str = baseText.getText();
		if (str.length() > 1000000) {
			//System.err.println("[Warning] coloringHTTPText: too long string. Skipping Highlight");
			return;
		}

		// 色を元に戻す
		javax.swing.text.MutableAttributeSet attributes = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setForeground(attributes, java.awt.Color.black);
		document.setCharacterAttributes(0, str.length(), attributes, false);

		// 色を変える
		com.google.re2j.Pattern pattern = com.google.re2j.Pattern.compile("([a-zA-Z0-9%.,/*_+-]+)=([a-zA-Z0-9%.,/*_+-]+)", com.google.re2j.Pattern.MULTILINE);
		com.google.re2j.Matcher matcher = pattern.matcher(str);
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = matcher.group(2);
			int key_start = matcher.start();
			int value_start = key_start + key.length() + 1;
			javax.swing.text.StyleConstants.setForeground(attributes, java.awt.Color.blue);
			document.setCharacterAttributes(key_start, key.length(), attributes, false);
			javax.swing.text.StyleConstants.setForeground(attributes, java.awt.Color.red);
			document.setCharacterAttributes(value_start, value.length(), attributes, false);
			//			System.out.println("key = " + key);
			//			System.out.println("value = " + value);
		}
	}

	private void updateSearchCount(int count) {
		String countLabel = "Not found";
		Color countColor = Color.GRAY;
		if (count < 0) {
			countLabel = "Too Long";
			countColor = Color.RED;
		} else if (count > 0) {
			countLabel = String.format("%d found", count);
			countColor = Color.YELLOW;
		}
		search_count.setBackground(countColor);
		search_count.setText(countLabel);
	}

	public void textChanged() {
		coloringHTTPText();
		updateSearchCount(coloringSearchText());
	}

	//public update(
}

