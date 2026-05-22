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
package packetproxy.gui.extensions;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import packetproxy.model.Extension;

/** Swing UI hooks for {@link Extension} implementations in the ui module. */
public abstract class GuiExtension extends Extension {

	public GuiExtension() {
		super();
	}

	public GuiExtension(String name, String path) throws Exception {
		super(name, path);
	}

	public JComponent createPanel() throws Exception {
		return null;
	}

	public JMenuItem historyClickHandler() {
		return null;
	}
}
