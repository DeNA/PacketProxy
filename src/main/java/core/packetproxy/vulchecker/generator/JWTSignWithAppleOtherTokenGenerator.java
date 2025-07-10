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
import java.net.URI;
import javax.swing.*;
import packetproxy.common.TokenHttpServer;
import packetproxy.gui.GUIMain;

public class JWTSignWithAppleOtherTokenGenerator extends Generator {

	@Override
	public String getName() {
		return "他サービスのApple id_tokenと入れ替え";
	}

	@Override
	public boolean generateOnStart() {
		return false;
	}

	String tokenFromBrowser;
	boolean cancelClicked;

	private TokenHttpServer server = null;

	@Override
	public String generate(String inputData) throws Exception {

		this.tokenFromBrowser = "";
		this.cancelClicked = false;

		JDialog dlg = new JDialog(GUIMain.getInstance());

		Rectangle rect = GUIMain.getInstance().getBounds();
		int width = 300;
		int height = 150;
		dlg.setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width,
				height); /* ド真ん中 */

		JPanel labels = new JPanel();
		labels.setLayout(new BoxLayout(labels, BoxLayout.X_AXIS));
		JLabel label = new JLabel("ブラウザに遷移してSign In with Appleします");
		label.setMaximumSize(new Dimension(Short.MAX_VALUE, 100));
		label.setHorizontalAlignment(JLabel.CENTER);
		label.setVerticalTextPosition(JLabel.CENTER);
		labels.add(label);
		labels.setMaximumSize(new Dimension(Short.MAX_VALUE, labels.getMaximumSize().height));

		JPanel buttons = new JPanel();
		buttons.setLayout(new FlowLayout());
		JButton ok = new JButton("ブラウザに遷移");
		ok.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				try {

					Desktop desktop = Desktop.getDesktop();
					desktop.browse(new URI("https://token.funacs.com/apple"));
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		buttons.add(ok);
		final JButton cancel = new JButton("キャンセル");
		cancel.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				cancelClicked = true;
				dlg.dispose();
			}
		});
		buttons.add(cancel);
		buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, 50));

		if (server == null) {

			server = new TokenHttpServer("localhost", 32350, token -> {

				tokenFromBrowser = token;
				dlg.dispose();
			});
		}
		server.start();

		JPanel main = new JPanel();
		main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
		main.add(labels);
		main.add(buttons);
		dlg.getContentPane().add(main);
		dlg.setModal(true);
		dlg.setVisible(true);

		if (cancelClicked) {

			throw new Exception("cancel");
		}

		GUIMain.getInstance().setAlwaysOnTop(true);
		GUIMain.getInstance().setVisible(true);

		// Need to wait for the server to finish sending the response data before
		// exiting
		Thread.sleep(100);
		server.stop();

		GUIMain.getInstance().setAlwaysOnTop(false);

		return tokenFromBrowser;
	}
}
