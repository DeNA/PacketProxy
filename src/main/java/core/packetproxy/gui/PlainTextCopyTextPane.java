/*
 * Copyright 2026 DeNA Co., Ltd.
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
import java.awt.datatransfer.StringSelection;
import javax.swing.JTextPane;

class PlainTextCopyTextPane extends JTextPane {

	@Override
	public void copy() {
		var selected = getSelectedText();
		if (selected == null || selected.isEmpty()) {
			super.copy();
			return;
		}

		var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		var selection = new StringSelection(prepareTextForCopy(selected));
		clipboard.setContents(selection, selection);
	}

	protected String prepareTextForCopy(String selected) {
		return selected;
	}
}
