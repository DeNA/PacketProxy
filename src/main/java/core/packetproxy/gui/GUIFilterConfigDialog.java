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
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;

import packetproxy.common.I18nString;

public class GUIFilterConfigDialog extends JDialog
{
	private static final long serialVersionUID = 1L;
    private int height = 500;
    private int width = 800;
    private JFrame owner;
				
	public void showDialog()
	{
		setModal(true);
		setVisible(true);
	}
	public GUIFilterConfigDialog(JFrame owner) throws Exception {
		super(owner);
		this.owner = owner;
		setTitle(I18nString.get("Manage filters"));
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
	    		dispose();
			}
		});
		
		Rectangle rect = owner.getBounds();
		setBounds(rect.x + rect.width/2 - width/2, rect.y + rect.height/2 - height/2, width, height); /* ド真ん中 */

		Container c = getContentPane();
		JPanel panel = new JPanel(); 
	    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
	    panel.add(new GUIFilterConfig(owner).createPanel());
		c.add(panel);

	}
}
