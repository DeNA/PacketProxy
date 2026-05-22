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

import java.io.File;
import java.util.EventListener;
import javax.swing.*;

public class WriteFileChooserWrapper {

	private static int EVENTLISTENER_IS_ALREADY_EXISTS = -1;
	private static int EVENTLISTENER_IS_ADDED = -1;
	private NativeFileChooser fileChooser;
	private JFrame owner;
	private String fileExtension;
	private String currentDirectory;
	protected FileChooserListener listener = null;

	public WriteFileChooserWrapper(JFrame owner, String fileExtension) {
		setFileChooser(owner, fileExtension, System.getProperty("user.home"));
	}

	public WriteFileChooserWrapper(JFrame owner, String fileExtension, String currentDirectory) {
		setFileChooser(owner, fileExtension, currentDirectory);
	}

	private void setFileChooser(JFrame owner, String fileExtension, String currentDirectory) {
		this.owner = owner;
		this.fileExtension = fileExtension;
		this.currentDirectory = currentDirectory;

		fileChooser = new NativeFileChooser(currentDirectory);
		fileChooser.addChoosableFileFilter("*." + fileExtension, fileExtension);
		fileChooser.setAcceptAllFileFilterUsed(false);
	}

	public void showSaveDialog() {
		int selected = fileChooser.showSaveDialog(owner);
		if (selected == NativeFileChooser.APPROVE_OPTION) {

			File file = fileChooser.getSelectedFile();
			if (null != listener) {

				String filePath = "";
				if (file.getName().matches(".+\\." + fileExtension)) {

					filePath = file.getAbsolutePath();
				} else {

					filePath = file.getAbsolutePath() + "." + fileExtension;
				}

				File finalFile = new File(filePath);
				if (finalFile.exists()) {
					int result = JOptionPane.showConfirmDialog(owner, "ファイルが既に存在しますが上書きしますか？", "Existing file",
							JOptionPane.YES_NO_CANCEL_OPTION);
					switch (result) {
						case JOptionPane.YES_OPTION :
							listener.onApproved(finalFile, fileExtension);
							return;
						case JOptionPane.NO_OPTION :
						case JOptionPane.CLOSED_OPTION :
							return;
						case JOptionPane.CANCEL_OPTION :
							listener.onCanceled();
							return;
					}
				}
				listener.onApproved(finalFile, fileExtension);
			}
		} else if (selected == NativeFileChooser.CANCEL_OPTION) {

			if (null != listener) {

				listener.onCanceled();
			}

		} else if (selected == NativeFileChooser.ERROR_OPTION) {

			if (null != listener) {

				listener.onError();
			}
		}
	}

	public interface FileChooserListener extends EventListener {

		void onApproved(File file, String extension);

		void onCanceled();

		void onError();
	}

	public int addFileChooserListener(FileChooserListener listener) {
		if (null != this.listener) {

			return EVENTLISTENER_IS_ALREADY_EXISTS;
		}
		this.listener = listener;
		return EVENTLISTENER_IS_ADDED;
	}
}
