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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;

import packetproxy.common.I18nString;
import packetproxy.model.CAs.CA;

public class GUIOptionExportCertificateAndPrivateKeyDialog extends JDialog {
    private JPanel cardPanel = new JPanel();
    private CardLayout cardLayout = new CardLayout();
    private JFrame owner;
    private CA ca;

    GUIOptionExportCertificateAndPrivateKeyDialog(JFrame owner, CA ca) {
        super(owner);
        this.owner = owner;
        this.ca = ca;
        setTitle(String.format(I18nString.get("Export %s"), ca.getName()));
        Rectangle rect = owner.getBounds();
        int height = 500;
        int width = 800;
        setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - height/2, width, height); /* ド真ん中 */

        Container c = getContentPane();
        cardPanel.setLayout(cardLayout);

        cardPanel.add(createSelectPanel(), "select panel");
        cardPanel.add(createP12PasswordPanel(), "p12 password panel");

        c.add(cardPanel);
    }

    private JPanel createSelectPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel chooseWhatToExport = new JLabel(I18nString.get("Choose what you want to export"));
        JLabel certificateGuide = new JLabel(I18nString.get("Export CA certificate used to view SSL packets. It needs to be registered in trusted CA list of Mac/Windows/Linux/Android/iOS"));
        panel.add(chooseWhatToExport);
        panel.add(certificateGuide);

        JRadioButton selectCertificatePEMButton = new JRadioButton(I18nString.get("Certificate(PEM format)"));
        JRadioButton selectCertificateDERButton = new JRadioButton(I18nString.get("Certificate(DER format)"));
        JRadioButton selectPrivateKeyPEMButton = new JRadioButton(I18nString.get("Private Key(PEM format)"));
        JRadioButton selectPrivateKeyDERButton = new JRadioButton(I18nString.get("Private Key(DER format)"));
        JRadioButton selectP12Button = new JRadioButton(I18nString.get("Certificate&Private Key(P12 format)"));
        ButtonGroup bgroup = new ButtonGroup();
        bgroup.add(selectCertificatePEMButton);
        bgroup.add(selectCertificateDERButton);
        bgroup.add(selectPrivateKeyPEMButton);
        bgroup.add(selectPrivateKeyDERButton);
        bgroup.add(selectP12Button);
        panel.add(selectCertificatePEMButton);
        panel.add(selectCertificateDERButton);
        panel.add(selectPrivateKeyPEMButton);
        panel.add(selectPrivateKeyDERButton);
        panel.add(selectP12Button);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton selectCancelButton = new JButton(I18nString.get("Cancel"));
        JButton selectDoneButton = new JButton(I18nString.get("Next"));
        buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, selectCancelButton.getMaximumSize().height));
        buttons.add(selectCancelButton);
        buttons.add(selectDoneButton);
        panel.add(buttons);

        selectCancelButton.addActionListener(e -> {
            dispose();
        });
        selectDoneButton.addActionListener(e -> {
            String extension = "";
            if (selectCertificatePEMButton.isSelected() || selectCertificateDERButton.isSelected()) {
                extension = "crt";
            } else if (selectPrivateKeyPEMButton.isSelected() || selectPrivateKeyDERButton.isSelected()) {
                extension = "key";
            } else if (selectP12Button.isSelected()) {
                cardLayout.show(cardPanel, "p12 password panel");
                return;
            } else {
                return;
            }
            WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, extension);
            filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {
                @Override
                public void onApproved(File file, String extension) {
                    try {
                        String path = file.getAbsolutePath();
                        if (selectCertificatePEMButton.isSelected()) ca.exportCertificatePEM(path);
                        else if (selectCertificateDERButton.isSelected()) ca.exportCertificateDER(path);
                        else if (selectPrivateKeyPEMButton.isSelected()) ca.exportPrivateKeyPEM(path);
                        else if (selectPrivateKeyDERButton.isSelected()) ca.exportPrivateKeyDER(path);
                        JOptionPane.showMessageDialog(owner, I18nString.get("Successfully exported to %s", path));
                        dispose();
                    } catch (java.io.FileNotFoundException e) {
                        JOptionPane.showMessageDialog(owner, I18nString.get("[Error] no such directory."));
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(owner, I18nString.get("[Error] failed to export."));
                        e.printStackTrace();
                    }
                }

                @Override
                public void onCanceled() {
                }

                @Override
                public void onError() {
                    JOptionPane.showMessageDialog(owner, I18nString.get("[Error] failed to export."));
                }
            });
            filechooser.showSaveDialog();
        });

        chooseWhatToExport.setAlignmentX(Component.LEFT_ALIGNMENT);
        certificateGuide.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectCertificatePEMButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectCertificateDERButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectPrivateKeyPEMButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectPrivateKeyDERButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectP12Button.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        return panel;
    }

    private JComponent label_and_object(String label_name, JComponent object) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        JLabel label = new JLabel(label_name);
        label.setPreferredSize(new Dimension(240, label.getMaximumSize().height));
        panel.add(label);
        object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
        panel.add(object);
        return panel;
    }

    private JPanel createP12PasswordPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPasswordField p12PasswordField = new JPasswordField();
        JComponent p12PasswordComponent = label_and_object(I18nString.get("Password_of_p12_file:"), p12PasswordField);
        panel.add(p12PasswordComponent);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton enterCancelButton = new JButton(I18nString.get("Cancel"));
        JButton enterDoneButton = new JButton(I18nString.get("Next"));
        buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, enterCancelButton.getMaximumSize().height));
        buttons.add(enterCancelButton);
        buttons.add(enterDoneButton);
        panel.add(buttons);

        enterCancelButton.addActionListener(e -> {
            p12PasswordField.setText("");
            cardLayout.show(cardPanel, "select panel");
        });
        enterDoneButton.addActionListener(e -> {
            WriteFileChooserWrapper filechooser = new WriteFileChooserWrapper(owner, "p12");
            filechooser.addFileChooserListener(new WriteFileChooserWrapper.FileChooserListener() {
                @Override
                public void onApproved(File file, String extension) {
                    try {
                        String path = file.getAbsolutePath();
                        ca.exportP12(path, p12PasswordField.getPassword());
                        JOptionPane.showMessageDialog(owner, I18nString.get("Successfully exported to %s", path));
                        dispose();
                    } catch (java.io.FileNotFoundException e) {
                        JOptionPane.showMessageDialog(owner, I18nString.get("[Error] no such directory."));
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(owner, I18nString.get("[Error] failed to export."));
                        e.printStackTrace();
                    }
                }

                @Override
                public void onCanceled() {
                }

                @Override
                public void onError() {
                    JOptionPane.showMessageDialog(owner, I18nString.get("[Error] failed to export."));
                }
            });
            filechooser.showSaveDialog();
        });

        p12PasswordComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        return panel;
    }

    public void showDialog() {
        setModal(true);
        setVisible(true);
    }
}
