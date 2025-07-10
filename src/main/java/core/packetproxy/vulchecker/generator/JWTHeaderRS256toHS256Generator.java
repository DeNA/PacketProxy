/*
 * Copyright 2023 DeNA Co., Ltd.
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

package packetproxy.vulchecker.generator;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import packetproxy.gui.GUIMain;

public class JWTHeaderRS256toHS256Generator extends Generator {

	@Override
	public String getName() {
		return "Header: alg: RS256 -> HS256";
	}

	@Override
	public boolean generateOnStart() {
		return false;
	}

	boolean cancelClicked;

	@Override
	public String generate(String inputData) throws Exception {

		cancelClicked = false;
		JDialog dlg = new JDialog(GUIMain.getInstance());

		Rectangle rect = GUIMain.getInstance().getBounds();
		int width = 400;
		int height = 300;
		dlg.setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width,
				height); /* ド真ん中 */

		JPanel labels = new JPanel();
		labels.setLayout(new BoxLayout(labels, BoxLayout.X_AXIS));
		labels.add(new JLabel("RSA Public Key?"));
		labels.setMaximumSize(new Dimension(Short.MAX_VALUE, labels.getMaximumSize().height));

		JTextArea area = new JTextArea();
		JScrollPane scrollpane = new JScrollPane(area);
		scrollpane.setMaximumSize(new Dimension(Short.MAX_VALUE, scrollpane.getMaximumSize().height));

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		JButton ok = new JButton("設定");
		ok.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				super.mouseClicked(e);
				dlg.dispose();
			}
		});
		buttons.add(ok);
		final JButton cancel = new JButton("キャンセル");
		cancel.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				super.mouseClicked(e);
				cancelClicked = true;
				dlg.dispose();
			}
		});
		buttons.add(cancel);

		JPanel main = new JPanel();
		main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
		main.add(labels);
		main.add(scrollpane);
		main.add(buttons);
		dlg.getContentPane().add(main);
		dlg.setModal(true);
		dlg.setVisible(true);

		if (cancelClicked) {

			throw new Exception("cancel");
		}

		String pubkey = area.getText();
		JWTAlgHS256 jwt = new JWTAlgHS256(inputData, pubkey);
		jwt.setHeaderValue("alg", "HS256");
		return jwt.toJwtString();
	}
}
