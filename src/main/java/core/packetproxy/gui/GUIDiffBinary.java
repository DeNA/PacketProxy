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

import static packetproxy.util.Logging.errWithStackTrace;

import java.awt.*;
import javax.swing.*;
import packetproxy.common.Binary;
import packetproxy.model.DiffBinary;
import packetproxy.model.DiffEventAdapter;
import packetproxy.model.DiffSet;

public class GUIDiffBinary extends GUIDiffBase {

	@Override
	protected DiffSet sortUniq(DiffSet ds) {
		String strOrig = "";
		String strTarg = "";
		try {

			strOrig = super.sortUniq(new Binary(ds.getOriginal()).toHexString().toString());
			strTarg = super.sortUniq(new Binary(ds.getTarget()).toHexString().toString());
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		return new DiffSet(strOrig.getBytes(), strTarg.getBytes());
	}

	public GUIDiffBinary() throws Exception {
	}

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

		clearDiff(textOrig);
		clearDiff(textTarg);

		DiffEventAdapter eventFordocOrig = new DiffEventAdapter() {

			public void foundDelDelta(int pos, int length) throws Exception {
				markDiff(textOrig, pos, length, delAttr);
			}

			public void foundChgDelta(int pos, int length) throws Exception {
				markDiff(textOrig, pos, length, chgAttr);
			}
		};

		DiffEventAdapter eventForTarget = new DiffEventAdapter() {

			public void foundInsDelta(int pos, int length) throws Exception {
				markDiff(textTarg, pos, length, insAttr);
			}

			public void foundChgDelta(int pos, int length) throws Exception {
				markDiff(textTarg, pos, length, chgAttr);
			}
		};
		DiffBinary.diffPerCharacter(ds, eventFordocOrig, eventForTarget);
	}
}
