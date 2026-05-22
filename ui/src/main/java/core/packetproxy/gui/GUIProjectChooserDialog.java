/*
 * Copyright 2025 DeNA Co., Ltd.
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import packetproxy.common.I18nString;

public class GUIProjectChooserDialog {

	private final JFrame owner;
	private final Projects projects;

	public GUIProjectChooserDialog(JFrame owner) {
		this.owner = owner;
		this.projects = new Projects();
	}

	private static class RecentProjectCellRenderer extends DefaultListCellRenderer {
		private int hoveredIndex = -1;

		public void setHoveredIndex(int index) {
			this.hoveredIndex = index;
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			if (value instanceof Projects.ProjectInfo) {
				var info = (Projects.ProjectInfo) value;
				var panel = new JPanel();
				panel.setLayout(new GridBagLayout());
				panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

				var gbc = new GridBagConstraints();
				gbc.fill = GridBagConstraints.HORIZONTAL;
				gbc.anchor = GridBagConstraints.WEST;
				gbc.insets = new Insets(0, 0, 2, 0);

				var nameLabel = new JLabel(info.getName());
				nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
				nameLabel.setOpaque(false);
				gbc.gridx = 0;
				gbc.gridy = 0;
				gbc.gridwidth = 2;
				gbc.weightx = 1.0;
				panel.add(nameLabel, gbc);

				var pathTitleLabel = new JLabel(I18nString.get("Path") + ": ");
				pathTitleLabel.setFont(pathTitleLabel.getFont().deriveFont(11f));
				pathTitleLabel.setForeground(Color.GRAY);
				pathTitleLabel.setOpaque(false);
				gbc.gridx = 0;
				gbc.gridy = 1;
				gbc.gridwidth = 1;
				gbc.weightx = 0.0;
				gbc.insets = new Insets(2, 0, 0, 5);
				panel.add(pathTitleLabel, gbc);

				var pathLabel = new JLabel(info.getPath());
				pathLabel.setFont(pathLabel.getFont().deriveFont(11f));
				pathLabel.setForeground(Color.GRAY);
				pathLabel.setOpaque(false);
				gbc.gridx = 1;
				gbc.gridy = 1;
				gbc.weightx = 1.0;
				gbc.insets = new Insets(2, 0, 0, 0);
				panel.add(pathLabel, gbc);

				var modifiedTitleLabel = new JLabel(I18nString.get("Last modified") + ": ");
				modifiedTitleLabel.setFont(modifiedTitleLabel.getFont().deriveFont(11f));
				modifiedTitleLabel.setForeground(Color.GRAY);
				modifiedTitleLabel.setOpaque(false);
				gbc.gridx = 0;
				gbc.gridy = 2;
				gbc.weightx = 0.0;
				gbc.insets = new Insets(0, 0, 0, 5);
				panel.add(modifiedTitleLabel, gbc);

				var modifiedLabel = new JLabel(info.getLastModified());
				modifiedLabel.setFont(modifiedLabel.getFont().deriveFont(11f));
				modifiedLabel.setForeground(Color.GRAY);
				modifiedLabel.setOpaque(false);
				gbc.gridx = 1;
				gbc.gridy = 2;
				gbc.weightx = 1.0;
				gbc.insets = new Insets(0, 0, 0, 0);
				panel.add(modifiedLabel, gbc);

				if (index == hoveredIndex) {
					panel.setOpaque(true);
					panel.setBackground(new Color(230, 240, 255));
				} else {
					panel.setOpaque(false);
				}

				return panel;
			}
			return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		}
	}

	public boolean chooseAndSetup() throws Exception {
		while (true) {
			final var result = new boolean[]{false};
			final var decided = new boolean[]{false};
			final var shouldExit = new boolean[]{false};
			var dialog = new JDialog(owner, I18nString.get("Welcome"), true);
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			dialog.addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosing(java.awt.event.WindowEvent e) {
					shouldExit[0] = true;
					dialog.dispose();
				}
			});
			var content = new JPanel();
			content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

			var btnTemp = new JButton(I18nString.get("Temporary project"));
			btnTemp.setAlignmentX(Component.CENTER_ALIGNMENT);
			btnTemp.addActionListener(e -> {
				try {
					projects.createTemporaryProject();
					result[0] = false;
					decided[0] = true;
					dialog.dispose();
				} catch (Exception ex) {
					errWithStackTrace(ex);
				}
			});
			content.add(wrap(btnTemp));

			var btnNew = new JButton(I18nString.get("Create new project"));
			btnNew.setAlignmentX(Component.CENTER_ALIGNMENT);
			btnNew.addActionListener(e -> {
				try {
					if (setupNewProject(dialog)) {
						result[0] = false;
						decided[0] = true;
						dialog.dispose();
					}
				} catch (Exception ex) {
					errWithStackTrace(ex);
				}
			});
			content.add(wrap(btnNew));

			var btnOpen = new JButton(I18nString.get("Open previous project"));
			btnOpen.setAlignmentX(Component.CENTER_ALIGNMENT);
			btnOpen.addActionListener(e -> {
				try {
					if (openByFileChooser(dialog)) {
						result[0] = true;
						decided[0] = true;
						dialog.dispose();
					}
				} catch (Exception ex) {
					errWithStackTrace(ex);
					JOptionPane.showMessageDialog(dialog,
							I18nString.get("Failed to open project") + "\n" + ex.getMessage(), I18nString.get("Error"),
							JOptionPane.ERROR_MESSAGE);
				}
			});
			content.add(wrap(btnOpen));

			var validRecents = projects.getValidRecentProjects();
			if (!validRecents.isEmpty()) {
				var recentLabel = new JLabel(I18nString.get("Recent Projects"));
				recentLabel.setFont(recentLabel.getFont().deriveFont(Font.BOLD, 13f));
				recentLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
				content.add(wrap(recentLabel));

				var listModel = new DefaultListModel<Projects.ProjectInfo>();
				for (var project : validRecents) {
					listModel.addElement(project);
				}

				var projectList = new JList<>(listModel);
				var renderer = new RecentProjectCellRenderer();
				projectList.setCellRenderer(renderer);
				projectList.setFocusable(false);

				projectList.addMouseListener(new java.awt.event.MouseAdapter() {
					@Override
					public void mousePressed(java.awt.event.MouseEvent e) {
						var index = projectList.locationToIndex(e.getPoint());
						if (index < 0) {
							return;
						}
						var bounds = projectList.getCellBounds(index, index);
						if (bounds == null || !bounds.contains(e.getPoint())) {
							return;
						}
						var info = listModel.getElementAt(index);
						try {
							projects.openProject(info.getPath());
							result[0] = true;
							decided[0] = true;
							dialog.dispose();
						} catch (Exception ex) {
							errWithStackTrace(ex);
							JOptionPane.showMessageDialog(dialog,
									I18nString.get("Failed to open project") + "\n" + ex.getMessage(),
									I18nString.get("Error"), JOptionPane.ERROR_MESSAGE);
						}
					}

					@Override
					public void mouseExited(java.awt.event.MouseEvent e) {
						renderer.setHoveredIndex(-1);
						projectList.repaint();
						projectList.setCursor(java.awt.Cursor.getDefaultCursor());
					}
				});

				projectList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
					@Override
					public void mouseMoved(java.awt.event.MouseEvent e) {
						var index = projectList.locationToIndex(e.getPoint());
						if (index < 0) {
							renderer.setHoveredIndex(-1);
							projectList.setCursor(java.awt.Cursor.getDefaultCursor());
							projectList.repaint();
							return;
						}
						var bounds = projectList.getCellBounds(index, index);
						if (bounds == null || !bounds.contains(e.getPoint())) {
							renderer.setHoveredIndex(-1);
							projectList.setCursor(java.awt.Cursor.getDefaultCursor());
							projectList.repaint();
							return;
						}
						renderer.setHoveredIndex(index);
						projectList.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
						projectList.repaint();
					}
				});

				var listScrollPane = new JScrollPane(projectList);
				listScrollPane.setPreferredSize(new Dimension(700, 280));
				listScrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
				listScrollPane.getViewport().setOpaque(true);
				projectList.setOpaque(true);
				content.add(wrap(listScrollPane));
			}

			var sc = new JScrollPane(content);
			dialog.setContentPane(sc);
			dialog.pack();
			dialog.setLocationRelativeTo(owner);
			dialog.setVisible(true);
			if (decided[0]) {

				return result[0];
			}
			if (shouldExit[0]) {

				System.exit(0);
			}
		}
	}

	private JPanel wrap(Component c) {
		var p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		p.add(c);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return p;
	}

	private boolean setupNewProject(Component parent) throws Exception {
		var panel = new JPanel(new GridBagLayout());
		var gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(5, 5, 5, 5);

		var label = new JLabel(I18nString.get("Enter project name") + ":");
		panel.add(label, gbc);

		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		var textField = new JTextField(20);
		panel.add(textField, gbc);

		var optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		var dialog = optionPane.createDialog(parent, I18nString.get("Create Project"));

		var okButton = Arrays.stream(optionPane.getComponents()).filter(comp -> comp instanceof JPanel)
				.flatMap(comp -> Arrays.stream(((JPanel) comp).getComponents()))
				.filter(button -> button instanceof JButton).map(button -> (JButton) button)
				.filter(btn -> btn.getText() != null && (btn.getText().equals("OK")
						|| btn.getText().equals(UIManager.getString("OptionPane.okButtonText"))))
				.findFirst().orElse(null);

		if (okButton != null) {
			okButton.setEnabled(false);
			var finalOkButton = okButton;
			textField.getDocument().addDocumentListener(new DocumentListener() {
				public void changedUpdate(DocumentEvent e) {
					updateButton();
				}

				public void removeUpdate(DocumentEvent e) {
					updateButton();
				}

				public void insertUpdate(DocumentEvent e) {
					updateButton();
				}

				private void updateButton() {
					finalOkButton.setEnabled(!textField.getText().trim().isEmpty());
				}
			});
		}

		dialog.setVisible(true);
		var result = optionPane.getValue();

		if (!Objects.equals(result, JOptionPane.OK_OPTION)) {
			return false;
		}

		var name = textField.getText().trim();
		if (name.isEmpty()) {
			return false;
		}

		projects.createNewProject(name);
		return true;
	}

	private boolean openByFileChooser(Component parent) throws Exception {
		var filechooser = new NativeFileChooser();
		filechooser.addChoosableFileFilter("*.sqlite3", "sqlite3");
		filechooser.setAcceptAllFileFilterUsed(false);
		var ok = filechooser.showOpenDialog(parent);
		if (ok != NativeFileChooser.APPROVE_OPTION) {
			return false;
		}
		var file = filechooser.getSelectedFile();
		try {
			projects.openProject(file.getAbsolutePath());
			return true;
		} catch (Exception ex) {
			errWithStackTrace(ex);
			JOptionPane.showMessageDialog(parent, I18nString.get("Failed to open project") + "\n" + ex.getMessage(),
					I18nString.get("Error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}
}
