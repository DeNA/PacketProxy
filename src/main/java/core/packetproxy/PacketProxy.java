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
import packetproxy.common.Utils;
import packetproxy.gui.GUIMain;
import packetproxy.gui.Splash;
import packetproxy.model.Database;

public class PacketProxy
{
	public static void main(String[] args) {

		if (Utils.supportedJava() == false) {
			JOptionPane.showMessageDialog(
					null,
					"PacketProxyはJDK 8のみで実行可能です",
					"エラー",
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
						"データベースの形式が更新されているため起動できません。\n現在のデータベースを削除して再起動しても良いですか？",
						"データベースの更新",
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
