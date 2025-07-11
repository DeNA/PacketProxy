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
import javax.swing.*;
import packetproxy.model.Diff;
import packetproxy.model.DiffEventAdapter;
import packetproxy.model.DiffSet;

public class GUIDiffRaw extends GUIDiffBase {

	protected JCheckBox jcCh;
	@Override
	protected DiffSet sortUniq(DiffSet ds) {
		String strOrig = sortUniq(new String(ds.getOriginal()));
		String strTarg = sortUniq(new String(ds.getTarget()));
		return new DiffSet(strOrig.getBytes(), strTarg.getBytes());
	}

	public GUIDiffRaw() throws Exception {
		jcCh = new JCheckBox("Character based (default: Line based)");
		jcCh.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				try {

					update();
				} catch (Exception e1) {

					e1.printStackTrace();
				}
			}
		});
		jc_panel.add(new JLabel("    ")); // spacer
		jc_panel.add(jcCh);
	}

	@Override
	public void update() throws Exception {
		DiffSet ds;
		if (jc.isSelected()) {

			ds = sortUniq(Diff.getInstance().getSet());
		} else {

			ds = Diff.getInstance().getSet();
		}
		textOrig.setData(ds.getOriginal(), false);
		textTarg.setData(ds.getTarget(), false);

		docOrig = textOrig.getStyledDocument();
		docTarg = textTarg.getStyledDocument();

		docOrig.setCharacterAttributes(0, docOrig.getLength(), defaultAttr, false);
		docTarg.setCharacterAttributes(0, docTarg.getLength(), defaultAttr, false);

		DiffEventAdapter eventFordocOrig = new DiffEventAdapter() {

			public void foundDelDelta(int pos, int length) throws Exception {
				docOrig.setCharacterAttributes(pos, length, delAttr, false);
			}

			public void foundChgDelta(int pos, int length) throws Exception {
				docOrig.setCharacterAttributes(pos, length, chgAttr, false);
			}
		};

		DiffEventAdapter eventForTarget = new DiffEventAdapter() {

			public void foundInsDelta(int pos, int length) throws Exception {
				docTarg.setCharacterAttributes(pos, length, insAttr, false);
			}

			public void foundChgDelta(int pos, int length) throws Exception {
				docTarg.setCharacterAttributes(pos, length, chgAttr, false);
			}
		};
		if (jcCh.isSelected()) { // Character based

			Diff.diffPerCharacter(ds, eventFordocOrig, eventForTarget);
		} else { // Line based

			Diff.diffPerLine(ds, eventFordocOrig, eventForTarget);
		}
	}
}
