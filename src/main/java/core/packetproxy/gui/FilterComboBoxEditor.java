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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.ComboBoxEditor;
import javax.swing.event.EventListenerList;

public class FilterComboBoxEditor implements ComboBoxEditor {
	final protected HintTextField editor;
	int caret;

	protected EventListenerList listenerList = new EventListenerList();

	public FilterComboBoxEditor() {
		editor = new HintTextField("フィルタ文字列　(ex: request == example.com && type == image)");
		editor.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				caret = editor.getCaretPosition();
				fireActionEvent(editor.getText());
			}
		});
	}

	public void addActionListener(ActionListener l) {
		listenerList.add(ActionListener.class, l);
	}

	public Component getEditorComponent() {
		return editor;
	}

	public Object getItem() {
		return editor.getText();
	}

	public void removeActionListener(ActionListener l) {
		listenerList.remove(ActionListener.class, l);
	}

	public void selectAll() {
	}

	// ドロップダウンリストから選択した時に呼ばれる
	public void setItem(Object newValue) {
		if (newValue instanceof String) {
			String str = (String) newValue;
			editor.setText(str);
			editor.setCaretPosition(caret);
			fireActionEvent(str);
		}
	}

	protected void fireActionEvent(String str) {
		Object listeners[] = listenerList.getListenerList();
		for (Object obj : listeners) {
			if (obj instanceof ActionListener) {
				ActionListener l = (ActionListener)obj;
		        ActionEvent actionEvent = new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, editor.getText());
	            l.actionPerformed(actionEvent);
			}
		}
	}
}
