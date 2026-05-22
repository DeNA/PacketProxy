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

import com.google.common.primitives.Bytes;
import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import java.util.List;

public class DiffBinary extends DiffBase {
	// static public void main(String[] args) {
	// try {
	// Diff diff = Diff.getInstance();
	// diff.markAsOriginal("hello\nw orld\naaaa\nhoge".getBytes());
	// diff.markAsTarget("hello\nworld\nhoge".getBytes());
	// diff.diff(new DiffEventAdapter() {
	// @Override public void foundDelDelta(int pos, int length) throws Exception {
	// Logging.log(String.format("Orig DEL: %d %d", pos, length)); }
	// @Override public void foundInsDelta(int pos, int length) throws Exception {
	// Logging.log(String.format("Orig INS: %d %d", pos, length)); }
	// @Override public void foundChgDelta(int pos, int length) throws Exception {
	// Logging.log(String.format("Orig CHG: %d %d", pos, length)); }
	// }, new DiffEventAdapter() {
	// @Override public void foundDelDelta(int pos, int length) throws Exception {
	// Logging.log(String.format("Targ DEL: %d %d", pos, length)); }
	// @Override public void foundInsDelta(int pos, int length) throws Exception {
	// Logging.log(String.format("Targ INS: %d %d", pos, length)); }
	// @Override public void foundChgDelta(int pos, int length) throws Exception {
	// Logging.log(String.format("Targ CHG: %d %d", pos, length)); }
	// });
	// } catch (Exception e) {
	// errWithStackTrace(e);
	// }
	// }

	private DiffBinary() {
	}

	static DiffBinary instance = null;

	public static DiffBinary getInstance() throws Exception {
		if (instance == null) {

			instance = new DiffBinary();
		}
		return instance;
	}

	public static void diffPerCharacter(DiffSet set, DiffEventListener original_event, DiffEventListener target_event)
			throws Exception {
		try {

			List<Byte> listOrig = Bytes.asList(set.getOriginal());
			List<Byte> listTarg = Bytes.asList(set.getTarget());

			Patch diff = DiffUtils.diff(listOrig, listTarg);

			List<Delta> deltas = diff.getDeltas();
			for (Delta delta : deltas) {

				Chunk chunkOrig = delta.getOriginal();
				Chunk chunkTarg = delta.getRevised();
				if (delta.getType() == Delta.TYPE.CHANGE) {

					original_event.foundChgDelta(chunkPositionPerByte(listOrig, chunkOrig),
							chunkLengthPerByte(chunkOrig));
					target_event.foundChgDelta(chunkPositionPerByte(listTarg, chunkTarg),
							chunkLengthPerByte(chunkTarg));
				} else if (delta.getType() == Delta.TYPE.INSERT) {

					target_event.foundInsDelta(chunkPositionPerByte(listTarg, chunkTarg),
							chunkLengthPerByte(chunkTarg));
				} else if (delta.getType() == Delta.TYPE.DELETE) {

					original_event.foundDelDelta(chunkPositionPerByte(listOrig, chunkOrig),
							chunkLengthPerByte(chunkOrig));
				}
			}
		} catch (Exception e) {

			errWithStackTrace(e);
		}
	}

	public static void diffPerLine(DiffSet set, DiffEventListener original_event, DiffEventListener target_event)
			throws Exception {
	}

	protected static int sumOfBytesPerByte(List<Byte> list) {
		int i = list.size();
		return 2 * i + (i - 1);
	}

	private static int chunkPositionPerByte(List<Byte> lines, Chunk a) {
		int index = a.getPosition();
		List<Byte> sublines = lines.subList(0, index);
		return sumOfBytesPerByte(sublines) + 1;
	}

	private static int chunkLengthPerByte(Chunk a) {
		return sumOfBytesPerByte((List<Byte>) a.getLines());
	}
}
