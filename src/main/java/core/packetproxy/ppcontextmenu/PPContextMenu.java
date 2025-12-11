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
package packetproxy.ppcontextmenu;

import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import javax.swing.JMenuItem;

public abstract class PPContextMenu {

	protected JMenuItem menuItem;
	protected HashMap<String, Object> dependentData;

	public abstract String getLabelName();

	public abstract void action() throws Exception;

	public void registerItem() {
		menuItem = new JMenuItem(getLabelName());
		menuItem.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent actionEvent) {
				try {

					action();
				} catch (Exception e) {

					log("Error: %s module something happened.", getLabelName());
					errWithStackTrace(e);
				}
			}
		});
	}

	public void setDependentData(HashMap<String, Object> hm) {
		this.dependentData = hm;
	}

	public JMenuItem getMenuItem() {
		return this.menuItem;
	}
}
