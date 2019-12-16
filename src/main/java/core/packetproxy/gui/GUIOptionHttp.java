package packetproxy.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import packetproxy.common.I18nString;
import packetproxy.model.ConfigString;

public class GUIOptionHttp {

	private JComboBox<String> combo = new JComboBox<>();
	private ConfigString configPriority = new ConfigString("PriorityOrderOfHttpVersions");
	
	public GUIOptionHttp() throws Exception {
		combo.setPrototypeDisplayValue("xxxxxxx");
		combo.addItem("HTTP1");
		combo.addItem("HTTP2");
		combo.setMaximumRowCount(combo.getItemCount());
		String priority = configPriority.getString();
		if (priority == null || priority.length() == 0) {
			configPriority.setString("HTTP2");
			priority = configPriority.getString();
		}
		combo.setSelectedItem(priority);
		combo.addItemListener(new ItemListener(){
			@Override
			public void itemStateChanged(ItemEvent event) {
				try {
					if (event.getStateChange() != ItemEvent.SELECTED || combo.getSelectedItem() == null) {
						return;
					}
					String priority = (String)combo.getSelectedItem();
					configPriority.setString(priority);
					combo.setSelectedItem(priority);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		combo.setMaximumSize(new Dimension(combo.getPreferredSize().width, combo.getMinimumSize().height));
	}

	public JPanel createPanel() throws Exception {
		JPanel panel = new JPanel();
		panel.setBackground(Color.WHITE);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(combo);
		panel.add(new JLabel(I18nString.get("has a high priority")));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Short.MAX_VALUE, panel.getMaximumSize().height));
		return panel;
	}

}
