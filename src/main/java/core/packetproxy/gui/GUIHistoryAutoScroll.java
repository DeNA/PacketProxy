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

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import packetproxy.util.PacketProxyUtility;

@SuppressWarnings("serial")
public class GUIHistoryAutoScroll extends JLabel {

	static private ImageIcon disabledIcon = new ImageIcon(GUIHistoryAutoScroll.class.getResource("/gui/auto_scroll_disabled.png"));
	static private ImageIcon enabledIcon = new ImageIcon(GUIHistoryAutoScroll.class.getResource("/gui/auto_scroll_enabled.png"));
	private Boolean isEnabled = false;

	public GUIHistoryAutoScroll() {
		super(disabledIcon);
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				doToggle();
			}
		});
	}
	
	public boolean isEnabled() {
		synchronized (isEnabled) {
			return isEnabled.booleanValue();
		}
	}
	
	public void doToggle() {
		synchronized (isEnabled) {
			if (isEnabled == true) {
				doDisable();
			} else {
				doEnable();
			}
		}
	}
	
	public void doEnable() {
		synchronized (isEnabled) {
			if (isEnabled == false) {
				setIcon(enabledIcon);
				isEnabled = true;
				PacketProxyUtility.getInstance().packetProxyLogErr("Auto scrolling was turned ON!");
			}
		}
	}

	public void doDisable() {
		synchronized (isEnabled) {
			if (isEnabled == true) {
				setIcon(disabledIcon);
				isEnabled = false;
				PacketProxyUtility.getInstance().packetProxyLogErr("Auto scrolling was turned OFF");
			}
		}
	}

}
