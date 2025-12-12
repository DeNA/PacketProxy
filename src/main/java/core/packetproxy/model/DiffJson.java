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
package packetproxy.model;

import static packetproxy.util.Logging.errWithStackTrace;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import java.util.Arrays;
import java.util.List;

public class DiffJson extends DiffBase {
	// static public void main(String[] args) {
	// try {
	// DiffJson diff = Diff.getInstance();
	// diff.markAsOriginal("hello\nw orld\naaaa\nhoge".getBytes());
	// diff.markAsTarget("hello\nworld\nhoge".getBytes());
	// diff.diff(new DiffEventAdapter() {
	// @Override public void foundDelDelta(int pos, int length) throws Exception {
	// System.out.println(String.format("Orig DEL: %d %d", pos, length)); }
	// @Override public void foundInsDelta(int pos, int length) throws Exception {
	// System.out.println(String.format("Orig INS: %d %d", pos, length)); }
	// @Override public void foundChgDelta(int pos, int length) throws Exception {
	// System.out.println(String.format("Orig CHG: %d %d", pos, length)); }
	// }, new DiffEventAdapter() {
	// @Override public void foundDelDelta(int pos, int length) throws Exception {
	// System.out.println(String.format("Targ DEL: %d %d", pos, length)); }
	// @Override public void foundInsDelta(int pos, int length) throws Exception {
	// System.out.println(String.format("Targ INS: %d %d", pos, length)); }
	// @Override public void foundChgDelta(int pos, int length) throws Exception {
	// System.out.println(String.format("Targ CHG: %d %d", pos, length)); }
	// });
	// } catch (Exception e) {
	// errWithStackTrace(e);
	// }
	// }

	private DiffJson() {
	}

	static DiffJson instance = null;

	public static DiffJson getInstance() throws Exception {
		if (instance == null) {

			instance = new DiffJson();
		}
		return instance;
	}

	public static void diffPerCharacter(DiffSet set, DiffEventListener original_event, DiffEventListener target_event)
			throws Exception {
		try {

			List<String> listOrig = Arrays.asList(new String(set.getOriginal()).split(""));
			List<String> listTarg = Arrays.asList(new String(set.getTarget()).split(""));

			Patch<String> diff = DiffUtils.diff(listOrig, listTarg);

			List<AbstractDelta<String>> deltas = diff.getDeltas();
			for (AbstractDelta<String> delta : deltas) {

				Chunk<String> chunkOrig = delta.getSource();
				Chunk<String> chunkTarg = delta.getTarget();
				if (delta.getType() == DeltaType.CHANGE) {

					original_event.foundChgDelta(chunkPositionPerCharacter(listOrig, chunkOrig),
							chunkLengthPerCharacter(chunkOrig));
					target_event.foundChgDelta(chunkPositionPerCharacter(listTarg, chunkTarg),
							chunkLengthPerCharacter(chunkTarg));
				} else if (delta.getType() == DeltaType.INSERT) {

					target_event.foundInsDelta(chunkPositionPerCharacter(listTarg, chunkTarg),
							chunkLengthPerCharacter(chunkTarg));
				} else if (delta.getType() == DeltaType.DELETE) {

					original_event.foundDelDelta(chunkPositionPerCharacter(listOrig, chunkOrig),
							chunkLengthPerCharacter(chunkOrig));
				}
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	public static void diffPerLine(DiffSet set, DiffEventListener original_event, DiffEventListener target_event)
			throws Exception {
		try {

			List<String> listOrig = Arrays.asList(new String(set.getOriginal()).split("\n"));
			List<String> listTarg = Arrays.asList(new String(set.getTarget()).split("\n"));

			Patch<String> diff = DiffUtils.diff(listOrig, listTarg);

			List<AbstractDelta<String>> deltas = diff.getDeltas();
			for (AbstractDelta<String> delta : deltas) {

				Chunk<String> chunkOrig = delta.getSource();
				Chunk<String> chunkTarg = delta.getTarget();
				if (delta.getType() == DeltaType.CHANGE) {

					original_event.foundChgDelta(chunkPositionPerLine(listOrig, chunkOrig),
							chunkLengthPerLine(chunkOrig));
					target_event.foundChgDelta(chunkPositionPerLine(listTarg, chunkTarg),
							chunkLengthPerLine(chunkTarg));
				} else if (delta.getType() == DeltaType.INSERT) {

					target_event.foundInsDelta(chunkPositionPerLine(listTarg, chunkTarg),
							chunkLengthPerLine(chunkTarg));
				} else if (delta.getType() == DeltaType.DELETE) {

					original_event.foundDelDelta(chunkPositionPerLine(listOrig, chunkOrig),
							chunkLengthPerLine(chunkOrig));
				}
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}
}
