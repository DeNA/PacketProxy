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
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import packetproxy.model.InterceptOption;
import packetproxy.model.Server;
import packetproxy.model.Servers;

public class GUIOptionInterceptDialog extends JDialog
{
	private static final long serialVersionUID = 1L;
	private JButton button_cancel = new JButton("キャンセル");
	private JButton button_set = new JButton("保存");
	private JComboBox<String> direction_combo = new JComboBox<String>();
	private JComboBox<String> type_combo = new JComboBox<String>();
	private JComboBox<String> relationship_combo = new JComboBox<String>();
	private JComboBox<String> method_combo = new JComboBox<String>();
	private JTextField text_pattern = new JTextField();
	private JComboBox<String> server_combo = new JComboBox<String>();
	private int height = 500;
	private int width = 500;
	private InterceptOption intercept_option = null;

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
	public InterceptOption showDialog(InterceptOption preset) throws Exception
	{
	    direction_combo.setSelectedItem(preset.getDirection().toString());
	    type_combo.setSelectedItem(preset.getType().toString());
	    relationship_combo.setSelectedItem(preset.getRelationship().toString());
	    method_combo.setSelectedItem(preset.getMethod().toString());
   		direction_combo.setSelectedItem(preset.getDirection().toString());
   		text_pattern.setText(preset.getPattern());
   		server_combo.setSelectedItem(preset.getServerName());
		setModal(true);
		setVisible(true);
		return intercept_option;
	}
	public InterceptOption showDialog()
	{
		setModal(true);
		setVisible(true);
		return intercept_option;
	}
	private JComponent createDirectionSetting() {
		direction_combo.addItem("CLIENT_REQUEST");
		direction_combo.addItem("SERVER_RESPONSE");
	    direction_combo.setEnabled(true);
	    direction_combo.setMaximumRowCount(3);
	    return label_and_object("Direction:", direction_combo);
	}
	private JComponent createMatchTypeSetting() {
		type_combo.addItem("REQUEST");
		type_combo.setEnabled(false);
		type_combo.setMaximumRowCount(1);
	    return label_and_object("Match Type:", type_combo);
	}
	private JComponent createRelationshipSetting() {
		relationship_combo.addItem("MATCHES");
		relationship_combo.addItem("DOES_NOT_MATCH");
		relationship_combo.addItem("WAS_INTERCEPTED");
		relationship_combo.setEnabled(true);
		relationship_combo.setMaximumRowCount(3);
	    return label_and_object("Relationship:", relationship_combo);
	}
	private JComponent createReplaceMethodSetting() {
		method_combo.addItem("SIMPLE");
		method_combo.addItem("REGEX");
		method_combo.addItem("BINARY");
		method_combo.setEnabled(true);
		method_combo.setMaximumRowCount(3);
	    return label_and_object("Method", method_combo);
	}
	private JComponent createPatternSetting() {
	    return label_and_object("Pattern:", text_pattern);
	}
	private JComponent createAppliedServers() throws Exception {
		server_combo.addItem("*");
		List<Server> servers = Servers.getInstance().queryAll();
		for (Server server : servers) {
			server_combo.addItem(server.toString());
		}
	    server_combo.setEnabled(true);
	    server_combo.setMaximumRowCount(servers.size());
	    return label_and_object("適用サーバ:", server_combo);
	}
	public GUIOptionInterceptDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle("設定");
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - width/2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel();
	    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
	    
	    panel.add(createMatchTypeSetting());
	    panel.add(createRelationshipSetting());
	    panel.add(createReplaceMethodSetting());
	    panel.add(createPatternSetting());
	    panel.add(createDirectionSetting());
	    panel.add(createAppliedServers());
	    
	    panel.add(buttons());

		c.add(panel);

	    button_cancel.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		intercept_option = null;
	    		dispose();
	    	}
	    });

	    button_set.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
	    		try {

	    			InterceptOption.Direction direction = null;
	    			if (direction_combo.getSelectedItem().toString().equals("CLIENT_REQUEST")) {
	    				direction = InterceptOption.Direction.CLIENT_REQUEST;
	    			} else if (direction_combo.getSelectedItem().toString().equals("SERVER_RESPONSE")) {
	    				direction = InterceptOption.Direction.SERVER_RESPONSE;
	    			}
	    			InterceptOption.Type type = null;
	    			if (type_combo.getSelectedItem().toString().equals("REQUEST")) {
	    				type = InterceptOption.Type.REQUEST;
	    			}
	    			InterceptOption.Relationship relationship = null;
	    			if (relationship_combo.getSelectedItem().toString().equals("MATCHES")) {
	    				relationship = InterceptOption.Relationship.MATCHES;
	    			} else if (relationship_combo.getSelectedItem().toString().equals("DOES_NOT_MATCH")) {
	    				relationship = InterceptOption.Relationship.DOES_NOT_MATCH;
	    			} else if (relationship_combo.getSelectedItem().toString().equals("WAS_INTERCEPTED")) {
	    				relationship = InterceptOption.Relationship.WAS_INTERCEPTED;
	    			}
	    			InterceptOption.Method method = null;
	    			if (method_combo.getSelectedItem().toString().equals("SIMPLE")) {
	    				method = InterceptOption.Method.SIMPLE;
	    			} else if (method_combo.getSelectedItem().toString().equals("REGEX")) {
	    				method = InterceptOption.Method.REGEX;
	    			} else if (method_combo.getSelectedItem().toString().equals("BINARY")) {
	    				method = InterceptOption.Method.BINARY;
	    			}
	    			
	    			assert(direction !=  null);
	    			assert(type != null);
	    			assert(relationship != null);
	    			assert(method != null);
	    			String server_str = server_combo.getSelectedItem().toString();
	    			intercept_option = new InterceptOption(direction,
	    					type,
	    					relationship,
	    					text_pattern.getText(),
	    					method,
	    					Servers.getInstance().queryByString(server_str));
	    			dispose();
	    		} catch (Exception e1) {
	    			e1.printStackTrace();
	    		}
	    	}
	    });
	}
}
