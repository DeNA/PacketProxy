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

import java.awt.Container;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JDialog;
import packetproxy.model.OneShotPacket;

@SuppressWarnings("serial")
public class GUIDecoderDialog extends JDialog {

	private GUIPacketData main_panel;

	public GUIDecoderDialog() throws Exception {
		super(GUIMain.getInstance());
		setTitle("Decoder");

		Rectangle rect = GUIMain.getInstance().getBounds();
		int width = rect.width - 100;
		int height = rect.height - 100;
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height); /* ド真ん中 */

		main_panel = new GUIPacketData();

		Container c = getContentPane();
		c.add(main_panel.createPanel());
	}

	public void setData(byte[] data) throws Exception {
		OneShotPacket oneshot = new OneShotPacket();
		oneshot.setData(data);
		main_panel.setOneShotPacket(oneshot);
	}

	public JComponent createPanel() {
		return main_panel.createPanel();
	}

	public void showDialog() {
		try {

			setModal(false);
			setVisible(true);
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}
}
