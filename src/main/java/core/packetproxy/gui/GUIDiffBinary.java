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
import javax.swing.*;
import packetproxy.common.Binary;
import packetproxy.model.DiffBinary;
import packetproxy.model.DiffSet;
import packetproxy.model.DiffEventAdapter;

public class GUIDiffBinary extends GUIDiffBase
{
	@Override
	protected DiffSet sortUniq(DiffSet ds) {
		String strOrig = "";
		String strTarg = "";
		try {
			strOrig = super.sortUniq(new Binary(ds.getOriginal()).toHexString().toString());
			strTarg = super.sortUniq(new Binary(ds.getTarget()).toHexString().toString());
		}catch (Exception e){
			e.printStackTrace();
		}
		return new DiffSet(strOrig.getBytes(), strTarg.getBytes());
	}
	public GUIDiffBinary() throws Exception {}

	@Override
	public void update() throws Exception {
		DiffSet ds;
		if (jc.isSelected()) {
			ds = sortUniq(DiffBinary.getInstance().getSet());
		} else {
			ds = DiffBinary.getInstance().getSet();
		}
		byte[] original = ds.getOriginal();
		byte[] target = ds.getTarget();
		if (original != null) {
			textOrig.setData(new Binary(ds.getOriginal()).toHexString().toString().getBytes(), false);
		}
		if (target != null) {
			textTarg.setData(new Binary(ds.getTarget()).toHexString().toString().getBytes(), false);
		}
		if (original == null || target == null) {
			return;
		}

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
		DiffBinary.diffPerCharacter(ds, eventFordocOrig, eventForTarget);
	}
}
