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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JTextField;

public class HintTextField extends JTextField
{
	private static final long serialVersionUID = 1L;

	private String hint;

	public void setHint(String hint){
		this.hint=hint;
	}
	public String getHint(){
		if(hint==null)
			hint="";
		return hint;
	}
	public void setText(String arg0) {
		super.setText(arg0);
		repaint();
	}

	public HintTextField(String hint){
		super();
		setHint(hint);
		addFocusListener(new FocusListener(){
			public void focusGained(FocusEvent arg0) {
				repaint();
			}
			public void focusLost(FocusEvent arg0) {
				repaint();
			}
		});
	}

	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D)g;

		if( hasFocus() ){
			return ;
		}
		if( getText().length()>0 ){
			return ;
		}
		if( getHint().length()<1){
			return ;
		}

		Font oldFont=g2.getFont();
		Color oldColor=g2.getColor();
		{
			g2.setFont(getFont().deriveFont(Font.ITALIC));
			g2.setColor(Color.GRAY);

			Insets insets=getBorder().getBorderInsets(this);
			int h=g2.getFontMetrics().getAscent();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.drawString(getHint(),insets.left,insets.top+h);
		}
		g2.setFont(oldFont);
		g2.setColor(oldColor);

	}
}
