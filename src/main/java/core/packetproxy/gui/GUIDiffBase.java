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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.text.StyledDocument;
import packetproxy.model.DiffSet;

abstract class GUIDiffBase {

	protected static final long serialVersionUID = 1L;
	protected int width;
	protected int height;
	protected JComponent panel;
	protected JPanel main_panel;
	protected RawTextPane textOrig;
	protected RawTextPane textTarg;
	protected StyledDocument docOrig;
	protected StyledDocument docTarg;
	protected JScrollPane scrollOrig;
	protected JScrollPane scrollTarg;
	protected javax.swing.text.MutableAttributeSet delAttr;
	protected javax.swing.text.MutableAttributeSet insAttr;
	protected javax.swing.text.MutableAttributeSet chgAttr;
	protected javax.swing.text.MutableAttributeSet defaultAttr;
	protected JCheckBox jc;
	protected JPanel jc_panel;

	protected String sortUniq(String str) {
		String[] lines = str.split("\n");
		List<String> list = Arrays.asList(lines);
		list.remove("");
		Set<String> set = new HashSet<String>();
		set.addAll(list);
		String[] result = set.toArray(new String[0]);
		Arrays.sort(result);
		return Arrays.stream(result).collect(Collectors.joining("\n"));
	}

	public GUIDiffBase() throws Exception {
		JPanel panelOrig = new JPanel();
		panelOrig.setLayout(new BoxLayout(panelOrig, BoxLayout.Y_AXIS));
		textOrig = new RawTextPane();
		textOrig.setEditable(true);
		JLabel labelOrig = new JLabel("Original");
		labelOrig.setAlignmentX(0.5f);
		panelOrig.add(labelOrig);
		scrollOrig = new JScrollPane(textOrig);
		scrollOrig.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollOrig.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		panelOrig.add(scrollOrig);

		JPanel panelTarg = new JPanel();
		panelTarg.setLayout(new BoxLayout(panelTarg, BoxLayout.Y_AXIS));
		textTarg = new RawTextPane();
		textTarg.setEditable(true);
		JLabel labelTarg = new JLabel("Target");
		labelTarg.setAlignmentX(0.5f);
		panelTarg.add(labelTarg);
		scrollTarg = new JScrollPane(textTarg);
		scrollTarg.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollTarg.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		panelTarg.add(scrollTarg);

		main_panel = new JPanel();
		main_panel.setLayout(new GridLayout(1, 2));
		main_panel.add(panelOrig);
		main_panel.add(panelTarg);

		jc = new JCheckBox("Sort & Uniq");
		jc.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					update();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});

		jc_panel = new JPanel();
		jc_panel.setLayout(new BoxLayout(jc_panel, BoxLayout.LINE_AXIS));
		jc_panel.add(jc);

		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(main_panel);
		panel.add(jc_panel);

		delAttr = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setBackground(delAttr, java.awt.Color.RED);
		chgAttr = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setBackground(chgAttr, java.awt.Color.YELLOW);
		insAttr = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setBackground(insAttr, java.awt.Color.GREEN);
		defaultAttr = new javax.swing.text.SimpleAttributeSet();
		javax.swing.text.StyleConstants.setBackground(defaultAttr, java.awt.Color.WHITE);
	}

	public JComponent createPanel() {
		return panel;
	}

	protected abstract DiffSet sortUniq(DiffSet ds);

	protected abstract void update() throws Exception;
}
