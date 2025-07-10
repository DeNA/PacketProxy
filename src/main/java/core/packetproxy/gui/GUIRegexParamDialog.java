package packetproxy.gui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import packetproxy.common.I18nString;
import packetproxy.model.RegexParam;

public class GUIRegexParamDialog extends JDialog {

	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private JTextField regex = new JTextField();
	private JTextField name = new JTextField();
	private int height = 500;
	private int width = 500;
	private RegexParam regexParam = null;

	private JComponent label_and_object(String label_name, JComponent object) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(150, label.getMaximumSize().height));
		panel.add(label);
		object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
		panel.add(object);
		return panel;
	}

	private JComponent buttons() {
		JPanel panel_button = new JPanel();
		panel_button.setLayout(new BoxLayout(panel_button, BoxLayout.X_AXIS));
		panel_button.setMaximumSize(new Dimension(Short.MAX_VALUE, button_set.getMaximumSize().height));
		panel_button.add(button_cancel);
		panel_button.add(button_set);
		return panel_button;
	}

	public RegexParam showDialog(RegexParam regexParam) throws Exception {
		this.regexParam = regexParam;
		regex.setText(regexParam.getRegex());
		name.setText(regexParam.getName());
		setModal(true);
		setVisible(true);
		return this.regexParam;
	}

	public RegexParam showDialog() {
		setModal(true);
		setVisible(true);
		return regexParam;
	}

	private JComponent createNameSetting() {
		return label_and_object("Param Name:", name);
	}

	private JComponent createRegexString() {
		return label_and_object("regex to pickup", regex);
	}

	public GUIRegexParamDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle("RegexParam setting");

		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width / 2 - width / 2, rect.y + rect.height / 2 - height / 2, width, height);

		Container c = getContentPane();
		JPanel panel = new JPanel();
		panel.add(createNameSetting());
		panel.add(createRegexString());
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.add(buttons());

		c.add(panel);

		button_cancel.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				regexParam = null;
				dispose();
			}
		});

		button_set.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				regexParam = new RegexParam(regexParam.getPacketId(), name.getText(), regex.getText());
				dispose();
			}
		});
	}
}
