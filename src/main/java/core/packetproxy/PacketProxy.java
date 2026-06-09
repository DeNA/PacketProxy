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
import java.sql.SQLException;
import java.util.Set;
import javax.swing.*;
import packetproxy.cli.app.CliRoot;
import packetproxy.common.I18nString;
import packetproxy.common.Utils;
import packetproxy.gui.GUIMain;
import packetproxy.gui.Splash;
import packetproxy.gulp.GulpTerminal;
import packetproxy.model.Database;
import packetproxy.util.Logging;
import picocli.CommandLine;

public class PacketProxy {
	public GUIMain gui;
	public ListenPortManager listenPortManager;

	/** picocli に委譲する CLI サブコマンド名のセット */
	private static final Set<String> CLI_SUBCOMMANDS = Set.of("server", "encode", "decode", "encoders");

	public PacketProxy() throws Exception {
	}

	public static void main(String[] args) throws Exception {
		// CLI サブコマンドの場合は picocli に委譲して終了（後方互換パスはそのまま）
		if (args.length > 0 && CLI_SUBCOMMANDS.contains(args[0])) {
			int exitCode = new CommandLine(new CliRoot()).execute(args);
			System.exit(exitCode);
			return;
		}

		// バイナリへの引数の解釈とセット
		String gulpMode = getOption("--gulp", args);
		String settingsJson = getOption("--settings-json", args);
		AppInitializer.setArgs(gulpMode != null, settingsJson);
		AppInitializer.initCore();

		if (gulpMode != null) {
			try {
				AppInitializer.initGulp();
				AppInitializer.initComponents();
			} catch (Exception e) {
				Logging.errWithStackTrace(e);
				System.exit(1);
			}

			Logging.log("Gulp Mode: " + settingsJson);
			GulpTerminal.run(settingsJson, gulpMode);
			System.exit(0);
		}

		if (!Utils.supportedJava()) {
			JOptionPane.showMessageDialog(null, I18nString.get("PacketProxy can be executed with JDK17 or later"),
					I18nString.get("Error"), JOptionPane.ERROR_MESSAGE);
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
						I18nString.get("Database error"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (option == JOptionPane.YES_OPTION) {
					try {
						File resource = new File((Database.getInstance()).getDatabasePath().toString());
						if (resource.exists()) {
							resource.delete();
						}
					} catch (Exception e2) {
						Logging.errWithStackTrace(e2);
					}
					continue;
				}
			} catch (Exception e) {
				Logging.errWithStackTrace(e);
			}
			break;
		}
		splash.close();
	}

	/**
	 * バイナリに渡される引数を解釈する
	 *
	 * @param option
	 *            取得したいオプションの文字列（末尾に=を含まない）
	 * @param args
	 *            対象の引数の配列
	 * @return 存在しない場合はnull, 存在する場合、最初の出現に対しての=以降の文字列（=が含まれない場合や=以降が存在しない場合は空文字）
	 */
	private static String getOption(String option, String[] args) {
		String addedOption = option + "=";

		for (String arg : args) {
			if (arg.equals(option))
				return "";
			if (arg.startsWith(addedOption))
				return arg.substring(addedOption.length());
		}
		return null;
	}

	public void start() throws Exception {
		startGUI();
		AppInitializer.initComponents();
	}

	private void startGUI() throws Exception {
		gui = GUIMain.getInstance();
		gui.setVisible(true);
	}
}
