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
package packetproxy;

import java.io.File;
import java.io.InputStream;
import java.sql.SQLException;

import javax.swing.JOptionPane;

import org.apache.commons.io.IOUtils;

import packetproxy.common.ClientKeyManager;
import packetproxy.common.I18nString;
import packetproxy.common.Utils;
import packetproxy.gui.GUIMain;
import packetproxy.gui.Splash;
import packetproxy.model.Database;
import packetproxy.EncoderManager;

public class PacketProxy
{
	public static void main(String[] args) {

		if (Utils.supportedJava() == false) {
			JOptionPane.showMessageDialog(
					null,
					I18nString.get("PacketProxy can be executed with JDK11 only"),
					I18nString.get("Error"),
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		Splash splash = new Splash();
		splash.show();
		
		while (true) {
			try {
				PacketProxy proxy = new PacketProxy();
				proxy.start();
			} catch (SQLException e) {
				int option = JOptionPane.showConfirmDialog(null,
						I18nString.get("Database read error.\nDelete the database and reboot?"),
						I18nString.get("Database error"),
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (option == JOptionPane.YES_OPTION) {
					try {
						File resource = new File((Database.getInstance()).getDatabasePath().toString());
						if (resource.exists()) {
							resource.delete();
						}
					}catch (Exception e2){
						e2.printStackTrace();
					}
					continue;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		}
		splash.close();
	}

	public GUIMain gui;
	public ListenPortManager listenPortManager;

	public PacketProxy() throws Exception {
		ClientKeyManager.initialize();
	}

	public void start() throws Exception {
		startGUI();
		listenPortManager = ListenPortManager.getInstance();
		// encoderのロードに1,2秒かかるのでここでロードをしておく（ここでしておかないと通信がacceptされたタイミングでロードする）
		EncoderManager.getInstance();
		VulCheckerManager.getInstance();
	}

	private void startGUI() throws Exception {
		String version = "1.0.0";
		InputStream versionStream = getClass().getResourceAsStream("/version");
		if (versionStream != null) {
			version = IOUtils.toString(versionStream);
		}
		gui = GUIMain.getInstance(String.format("PacketProxy %s", version));
		gui.setVisible(true);
	}
}
