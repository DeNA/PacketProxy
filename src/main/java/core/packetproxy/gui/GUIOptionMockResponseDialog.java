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

import packetproxy.common.I18nString;
import packetproxy.model.MockResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GUIOptionMockResponseDialog extends JDialog
{
	private static final long serialVersionUID = 1L;
	private static final String DEFAULT_MOCK_RESPONSE = "HTTP/1.0 200 OK\nServer: MockServer\nContent-type: text/html\n\nMock response\n";
	private JButton button_cancel = new JButton(I18nString.get("Cancel"));
	private JButton button_set = new JButton(I18nString.get("Save"));
	private HintTextField text_ip = new HintTextField("(ex.) aaa.bbb.ccc.com or 1.2.3.4");
	private HintTextField text_port = new HintTextField("(ex.) 80");
	private HintTextField text_path = new HintTextField("(ex.) \\/sample\\/.*");
	private HintTextArea text_mock_response = new HintTextArea("(ex.) "+DEFAULT_MOCK_RESPONSE, 600, 300);
	private HintTextField text_comment = new HintTextField("(ex.) game server for test");
	private int height = 500;
	private int width = 700;
	private MockResponse mockResponse = null;

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

	private JComponent label_and_object_no_set_maximumsize(String label_name, JComponent object) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		JLabel label = new JLabel(label_name);
		label.setPreferredSize(new Dimension(150, label.getMaximumSize().height));
		panel.add(label);
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
	public MockResponse showDialog(MockResponse preset)
	{
   		text_ip.setText(preset.getIp());
		text_port.setText(Integer.toString(preset.getPort()));
		text_path.setText(preset.getPath());
		text_mock_response.setText(preset.getMockResponse());
		text_comment.setText(preset.getComment());

		setModal(true);
		setVisible(true);
		if (mockResponse != null) {
			preset.setIp(text_ip.getText());
			preset.setPort(Integer.parseInt(text_port.getText()));
			preset.setPath(text_path.getText());
			preset.setMockResponse(text_mock_response.getText());
			preset.setComment(text_comment.getText());
			return preset;
		}
		return mockResponse;
	}
	public MockResponse showDialog()
	{
		EventQueue.invokeLater(new Runnable() {
			@Override public void run() {
				button_cancel.requestFocusInWindow();
			}
		});
		text_mock_response.setText(DEFAULT_MOCK_RESPONSE);
		setModal(true);
		setVisible(true);
		return mockResponse;
	}

	private JComponent createIpSetting() {
	    return label_and_object(I18nString.get("Server name:"), text_ip);
	}
	private JComponent createPortSetting() {
	    return label_and_object(I18nString.get("Server port:"), text_port);
	}
	private JComponent createPathSetting() {
		return label_and_object(I18nString.get("Path(regex):"), text_path);
	}
	private JComponent createMockResponseSetting() {
		return label_and_object_no_set_maximumsize(I18nString.get("Mock response:"), text_mock_response);
	}
	private JComponent createCommentSetting() {
	    return label_and_object(I18nString.get("Comments:"), text_comment);
	}

	public GUIOptionMockResponseDialog(JFrame owner, String ip, int port, String path, String mockResponse, String comment) throws Exception{
		this(owner);
		text_ip.setText(ip);
		text_port.setText(Integer.toString(port));
		text_path.setText(path);
		text_mock_response.setText(mockResponse);
		text_comment.setText(comment);
	}

	public GUIOptionMockResponseDialog(JFrame owner) throws Exception {
		super(owner);
		setTitle(I18nString.get("Server setting"));
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - height/2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel(); 
	    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
	    
	    panel.add(createIpSetting());
	    panel.add(createPortSetting());
		panel.add(createPathSetting());
		panel.add(createMockResponseSetting());
	    panel.add(createCommentSetting());
	    
	    panel.add(buttons());

		c.add(panel);

	    button_cancel.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
				mockResponse = null;
	    		dispose();
	    	}
	    });

	    button_set.addActionListener(new ActionListener() {
	    	@Override
			public void actionPerformed(ActionEvent e) {
				mockResponse = new MockResponse(text_ip.getText(),
	    				Integer.parseInt(text_port.getText()),
	    				text_path.getText(),
	    				text_mock_response.getText(),
	    				text_comment.getText());
	    		dispose();
	    	}
	    });
	}
}
