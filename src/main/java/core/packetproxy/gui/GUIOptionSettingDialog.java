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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import packetproxy.EncoderManager;

public class GUIOptionSettingDialog extends JDialog
{
	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton("キャンセル");
	private JButton button_set = new JButton("保存");
	private JTextField text_listen_port = new JTextField();
	private JTextField text_nexthop_ip = new JTextField();
	private JTextField text_nexthop_port = new JTextField();
	JRadioButton radio_forward = new JRadioButton("次のプロキシに転送",true);
	JRadioButton radio_direct = new JRadioButton("ゲームサーバに直接接続");
	JComboBox<String> combo = new JComboBox<String>();
	private int height = 500;
	private int width = 500;
	private HashMap<String,Object> map = new HashMap<String,Object>();

	private JComponent label_and_object(String label_name, JComponent object)
	{
		JPanel panel = new JPanel();
	    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

	    JLabel label = new JLabel(label_name);
	    label.setPreferredSize(new Dimension(150, label.getMaximumSize().height));
	    panel.add(label);
	    
	    object.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getMaximumSize().height * 2));
	    panel.add(object);
		
		return panel;
	}
	
	private JComponent buttons()
	{
	    JPanel panel_button = new JPanel();

	    panel_button.setLayout(new BoxLayout(panel_button, BoxLayout.X_AXIS));
	    panel_button.setMaximumSize(new Dimension(Short.MAX_VALUE, button_set.getMaximumSize().height));
	    panel_button.add(button_cancel);
	    panel_button.add(button_set);

	    return panel_button;
	}
	
	public HashMap<String,Object> showDialog(HashMap<String,Object> preset)
	{
   		text_listen_port.setText((String)preset.get("Listen Port")); 
  		combo.setSelectedItem(preset.get("Encode Module"));
   		//radio_forward.isSelected() ? "true" : "false");map.put("forward", 
   		text_nexthop_ip.setText((String)preset.get("Next-hop IP"));
   		text_nexthop_port.setText((String)preset.get("Next-hop port"));
		setModal(true);
		setVisible(true);
		return map;
	}
	
	public HashMap<String,Object> showDialog()
	{
		setModal(true);
		setVisible(true);
		return map;
	}

	private JComponent createModuleSetting() throws Exception
	{
		String[] names = EncoderManager.getInstance().getEncoderNameList();
		for (int i = 0; i < names.length; i++) {
			combo.addItem(names[i]);
		}
	    combo.setEnabled(true);
	    combo.setMaximumRowCount(names.length);
	    return label_and_object("Encodeモジュール:", combo);
	}
	
	private JComponent createProxySetting()
	{
	    JPanel panel = new JPanel();
	    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

	    ButtonGroup group = new ButtonGroup();
	    group.add(radio_forward);
	    group.add(radio_direct);
	    panel.add(radio_forward);
	    panel.add(radio_direct);

	    radio_forward.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		if (radio_forward.isSelected()) {
	    			text_nexthop_ip.setEnabled(false);
	    			text_nexthop_port.setEnabled(false);
	    		}
	    	}
	    });
	    
	    radio_direct.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		if (radio_direct.isSelected()) {
	    			text_nexthop_ip.setEnabled(true);
	    			text_nexthop_port.setEnabled(true);
	    		}
	    	}
	    });

	    text_nexthop_ip.setEnabled(false);
	    text_nexthop_port.setEnabled(false);

	    JPanel panel_proxy = new JPanel();
	    panel_proxy.setLayout(new BoxLayout(panel_proxy, BoxLayout.Y_AXIS));
	    panel_proxy.add(label_and_object("接続方法:", panel));
	    panel_proxy.add(label_and_object("ゲームサーバIP:", text_nexthop_ip));
	    panel_proxy.add(label_and_object("ゲームサーバPort:", text_nexthop_port));
	    return panel_proxy;
	}
	
	public GUIOptionSettingDialog(JFrame owner) throws Exception
	{
		super(owner);
		setTitle("設定");
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - width/2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel(); 
	    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
	    
	    panel.add(label_and_object("Listen Port:", text_listen_port));
	    panel.add(createModuleSetting());
	    panel.add(createProxySetting());
	    
	    panel.add(buttons());

		c.add(panel);

	    button_cancel.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		map.clear();
	    		map = null;
	    		dispose();
	    	}
	    });

	    button_set.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		map.put("Enabled", true);
	    		map.put("Listen Port", text_listen_port.getText());
	    		map.put("Encode Module", combo.getSelectedItem().toString());
	    		map.put("forward", radio_forward.isSelected() ? "true" : "false");
	    		map.put("Next-hop IP", text_nexthop_ip.getText());
	    		map.put("Next-hop port", text_nexthop_port.getText());
	    		dispose();
	    	}
	    });
	}
	
	public void setSetButtonListener(java.awt.event.MouseAdapter mouseAdapter)
	{
		button_set.addMouseListener(mouseAdapter);
	}
	
}
