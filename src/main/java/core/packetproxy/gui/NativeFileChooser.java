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

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import packetproxy.util.PacketProxyUtility;

/**
 * A file chooser that uses the native file dialog on Mac (Finder) 
 * and falls back to JFileChooser on other platforms.
 */
public class NativeFileChooser {

    public static final int APPROVE_OPTION = JFileChooser.APPROVE_OPTION;
    public static final int CANCEL_OPTION = JFileChooser.CANCEL_OPTION;
    public static final int ERROR_OPTION = JFileChooser.ERROR_OPTION;

    /**
     * Internal class to store file filter description and extensions together.
     */
    private static class FilterEntry {
        final String description;
        final String[] extensions;

        FilterEntry(String description, String[] extensions) {
            this.description = description;
            this.extensions = extensions;
        }
    }

    private File selectedFile;
    private File currentDirectory;
    private String dialogTitle;
    private List<FilterEntry> fileFilters = new ArrayList<>();
    private boolean acceptAllFileFilterUsed = true;

    public NativeFileChooser() {
        this.currentDirectory = new File(System.getProperty("user.home"));
    }

    public NativeFileChooser(String currentDirectoryPath) {
        this.currentDirectory = new File(currentDirectoryPath);
    }

    public void setCurrentDirectory(File dir) {
        this.currentDirectory = dir;
    }

    public void setDialogTitle(String title) {
        this.dialogTitle = title;
    }

    public void setAcceptAllFileFilterUsed(boolean b) {
        this.acceptAllFileFilterUsed = b;
    }

    /**
     * Add a file filter with description and extensions.
     * @param description The description (e.g., "*.sqlite3", "Client Certificate file (*.jks)")
     * @param extensions The file extensions without dots (e.g., "sqlite3", "json")
     */
    public void addChoosableFileFilter(String description, String... extensions) {
        fileFilters.add(new FilterEntry(description, extensions));
    }

    /**
     * Set file filter using FileNameExtensionFilter for compatibility.
     */
    public void setFileFilter(FileNameExtensionFilter filter) {
        fileFilters.clear();
        fileFilters.add(new FilterEntry(filter.getDescription(), filter.getExtensions()));
    }

    /**
     * Add file filter using FileNameExtensionFilter for compatibility.
     */
    public void addChoosableFileFilter(FileNameExtensionFilter filter) {
        fileFilters.add(new FilterEntry(filter.getDescription(), filter.getExtensions()));
    }

    public File getSelectedFile() {
        return selectedFile;
    }

    public void setSelectedFile(File file) {
        this.selectedFile = file;
    }

    /**
     * Show an open dialog.
     * @param parent The parent component
     * @return APPROVE_OPTION if a file was selected, CANCEL_OPTION otherwise
     */
    public int showOpenDialog(Component parent) {
        if (PacketProxyUtility.getInstance().isMac()) {
            return showNativeOpenDialog(parent);
        } else {
            return showSwingOpenDialog(parent);
        }
    }

    /**
     * Show a save dialog.
     * @param parent The parent component
     * @return APPROVE_OPTION if a file was selected, CANCEL_OPTION otherwise
     */
    public int showSaveDialog(Component parent) {
        if (PacketProxyUtility.getInstance().isMac()) {
            return showNativeSaveDialog(parent);
        } else {
            return showSwingSaveDialog(parent);
        }
    }

    /**
     * Get the Frame ancestor of the given component.
     * @param parent The component to find the Frame ancestor for
     * @return The Frame ancestor, or null if parent is null or no Frame ancestor exists
     */
    private Frame getFrame(Component parent) {
        if (parent == null) {
            return null;
        }
        if (parent instanceof Frame) {
            return (Frame) parent;
        }
        return (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
    }

    private FilenameFilter createFilenameFilter() {
        if (fileFilters.isEmpty()) {
            return null;
        }
        return (dir, name) -> {
            if (acceptAllFileFilterUsed) {
                return true;
            }
            String lowerName = name.toLowerCase();
            for (FilterEntry entry : fileFilters) {
                for (String ext : entry.extensions) {
                    if (lowerName.endsWith("." + ext.toLowerCase())) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    private int showNativeOpenDialog(Component parent) {
        Frame frame = getFrame(parent);
        FileDialog dialog = new FileDialog(frame, dialogTitle != null ? dialogTitle : "Open", FileDialog.LOAD);
        
        if (currentDirectory != null) {
            dialog.setDirectory(currentDirectory.getAbsolutePath());
        }

        // Use FilenameFilter for filtering files by extension
        // Note: setFile() should NOT be used for filtering as it sets the filename field, not the filter
        FilenameFilter filter = createFilenameFilter();
        if (filter != null && !acceptAllFileFilterUsed) {
            dialog.setFilenameFilter(filter);
        }

        dialog.setVisible(true);

        String file = dialog.getFile();
        String directory = dialog.getDirectory();
        
        if (file != null && directory != null) {
            selectedFile = new File(directory, file);
            return APPROVE_OPTION;
        }
        
        return CANCEL_OPTION;
    }

    private int showNativeSaveDialog(Component parent) {
        Frame frame = getFrame(parent);
        FileDialog dialog = new FileDialog(frame, dialogTitle != null ? dialogTitle : "Save", FileDialog.SAVE);
        
        if (currentDirectory != null) {
            dialog.setDirectory(currentDirectory.getAbsolutePath());
        }

        if (selectedFile != null) {
            dialog.setFile(selectedFile.getName());
        }

        dialog.setVisible(true);

        String file = dialog.getFile();
        String directory = dialog.getDirectory();
        
        if (file != null && directory != null) {
            selectedFile = new File(directory, file);
            return APPROVE_OPTION;
        }
        
        return CANCEL_OPTION;
    }

    private int showSwingOpenDialog(Component parent) {
        JFileChooser chooser = new JFileChooser();
        
        if (currentDirectory != null) {
            chooser.setCurrentDirectory(currentDirectory);
        }
        
        if (dialogTitle != null) {
            chooser.setDialogTitle(dialogTitle);
        }

        chooser.setAcceptAllFileFilterUsed(acceptAllFileFilterUsed);
        
        for (FilterEntry entry : fileFilters) {
            if (entry.extensions.length > 0) {
                chooser.addChoosableFileFilter(new FileNameExtensionFilter(entry.description, entry.extensions));
            }
        }

        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        int result = chooser.showOpenDialog(parent);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            return APPROVE_OPTION;
        }
        
        return CANCEL_OPTION;
    }

    private int showSwingSaveDialog(Component parent) {
        JFileChooser chooser = new JFileChooser();
        
        if (currentDirectory != null) {
            chooser.setCurrentDirectory(currentDirectory);
        }
        
        if (dialogTitle != null) {
            chooser.setDialogTitle(dialogTitle);
        }

        if (selectedFile != null) {
            chooser.setSelectedFile(selectedFile);
        }

        chooser.setAcceptAllFileFilterUsed(acceptAllFileFilterUsed);
        
        for (FilterEntry entry : fileFilters) {
            if (entry.extensions.length > 0) {
                chooser.addChoosableFileFilter(new FileNameExtensionFilter(entry.description, entry.extensions));
            }
        }

        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        int result = chooser.showSaveDialog(parent);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            return APPROVE_OPTION;
        }
        
        return CANCEL_OPTION;
    }
}
