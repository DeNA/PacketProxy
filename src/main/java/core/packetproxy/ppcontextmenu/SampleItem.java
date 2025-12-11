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

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FileUtils;
import packetproxy.gui.GUIPacket;

public class SampleItem extends PPContextMenu {

	@Override
	public String getLabelName() {
		return "sample implementation";
	}

	@Override
	public void action() throws Exception {
		JFileChooser saveFile = new JFileChooser("packet.dat");
		saveFile.setAcceptAllFileFilterUsed(false);
		saveFile.addChoosableFileFilter(new FileNameExtensionFilter("データファイル (.dat)", "dat"));
		saveFile.showSaveDialog((JFrame) this.dependentData.get("main_frame"));
		File file = saveFile.getSelectedFile();
		GUIPacket gui_packet = (GUIPacket) this.dependentData.get("gui_packet");
		byte[] data = gui_packet.getPacket().getReceivedData();
		FileUtils.writeByteArrayToFile(file, data);
		JOptionPane.showMessageDialog((JFrame) this.dependentData.get("main_frame"),
				String.format("%sに保存しました！", file.getPath()));
	}
}
