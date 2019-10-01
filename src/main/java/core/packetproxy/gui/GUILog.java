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

import java.awt.Color;
import java.awt.BorderLayout;
import javax.swing.text.Position;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.StyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.BadLocationException;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;

public class GUILog
{
	private JTextPane text;
	private JScrollPane scrollPane;
	private JPanel mainPanel;
	private static GUILog instance;
	private Object thread_lock;
	public static GUILog getInstance()
	{
		if (instance == null) {
			instance = new GUILog();
		}
		return instance;
	}

	public GUILog() {
		text = new JTextPane();
		text.setEditable(false);
		scrollPane = new JScrollPane(text);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(scrollPane, BorderLayout.CENTER);
		thread_lock = new Object();
	}

	public JComponent createPanel() {
		return mainPanel;
	}

	public void append(String s) {
		try {
			synchronized (thread_lock) {
				StyledDocument doc = text.getStyledDocument();
				doc.insertString(doc.getLength(), s + "\n\r", null);
			}
		} catch (BadLocationException ex) {
		}
	}

	public void appendErr(String s) {
		try {
			synchronized (thread_lock) {
				SimpleAttributeSet keyWord = new SimpleAttributeSet();
				StyleConstants.setBackground(keyWord, new Color(240, 150, 150));
				StyleConstants.setBold(keyWord, true);
				StyledDocument doc = text.getStyledDocument();
				doc.insertString(doc.getLength() - 1, s + "\n", keyWord);
			}
		} catch (BadLocationException ex) {
		}
	}
}
