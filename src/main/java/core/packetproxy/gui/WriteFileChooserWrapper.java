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

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.EventListener;

public class WriteFileChooserWrapper {

    private static int EVENTLISTENER_IS_ALREADY_EXISTS = -1;
    private static int EVENTLISTENER_IS_ADDED = -1;
    private JFileChooser fileChooser;
    private JFrame owner;
    private String fileExtension;
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

        fileChooser = new JFileChooser() {
            @Override
            public void approveSelection() {
                File f = getSelectedFile();
                File file;
                if (f.getName().matches(".+\\."+fileExtension)) {
                    file = f;
                } else {
                    file = new File(f.getAbsolutePath() + "." + fileExtension);
                }
                if (file.exists() && getDialogType() == SAVE_DIALOG) {
                    int result = JOptionPane.showConfirmDialog(this, "ファイルが既に存在しますが上書きしますか？", "Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
                    switch (result) {
                        case JOptionPane.YES_OPTION:
                            super.approveSelection();
                            return;
                        case JOptionPane.NO_OPTION:
                            return;
                        case JOptionPane.CLOSED_OPTION:
                            return;
                        case JOptionPane.CANCEL_OPTION:
                            cancelSelection();
                            return;
                    }
                }
                super.approveSelection();
            }
        };
        fileChooser.setCurrentDirectory(new File(currentDirectory));
        fileChooser.setFileFilter(new FileNameExtensionFilter("*." + fileExtension, fileExtension));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }

    public void showSaveDialog(){
        int selected = fileChooser.showSaveDialog(SwingUtilities.getRoot(owner));
        if (selected == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if(null!=listener) {
                String filePath = "";
                if (file.getName().matches(".+\\." + fileExtension)) {
                    filePath = file.getAbsolutePath();
                } else {
                    filePath = file.getAbsolutePath() + "." + fileExtension;
                }
                listener.onApproved(new File(filePath), fileExtension);
            }
        }else if(selected == JFileChooser.CANCEL_OPTION){
            if(null!=listener) {
                listener.onCanceled();
            }

        }else if(selected == JFileChooser.ERROR_OPTION){
            if(null!=listener) {
                listener.onError();
            }
        }
    }

    public interface FileChooserListener extends EventListener {
        public void onApproved(File file, String extension);
        public void onCanceled();
        public void onError();
    }

    public int addFileChooserListener(FileChooserListener listener) {
        if(null!=this.listener){
            return EVENTLISTENER_IS_ALREADY_EXISTS;
        }
        this.listener = listener;
        return EVENTLISTENER_IS_ADDED;
    }
}
