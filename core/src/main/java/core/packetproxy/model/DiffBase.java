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

import difflib.Chunk;
import java.util.List;
import javax.swing.event.EventListenerList;

public abstract class DiffBase {

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
	//
	public DiffBase() {
	}

	protected EventListenerList diffEventListenerList = new EventListenerList();
	protected byte[] orig = null;
	protected DiffSet set;

	public boolean isOriginalSet() {
		return this.orig != null ? true : false;
	}

	public void markAsOriginal(byte[] orig) throws Exception {
		if (orig != null && orig.length > 200 * 1024) {

			throw new Exception("Text is Too Long!");
		}
		this.orig = orig;
	}

	public void clearAsOriginal() throws Exception {
		this.orig = null;
	}

	public void markAsTarget(byte[] target) throws Exception {
		if (target != null && target.length > 200 * 1024) {

			throw new Exception("Text is Too Long!");
		}
		this.set = new DiffSet(this.orig, target);
	}

	public DiffSet getSet() {
		return set;
	}

	protected static int sumOfCharactersPerLine(List<String> list) {
		return list.stream().mapToInt(s -> s.length() + 1).sum();
	}

	protected static int sumOfCharactersPerCharacter(List<String> list) {
		return list.stream().mapToInt(s -> s.length()).sum();
	}

	protected static int chunkPositionPerLine(List<String> lines, Chunk a) {
		int index = a.getPosition();
		List<String> sublines = lines.subList(0, index);
		return sumOfCharactersPerLine(sublines);
	}

	protected static int chunkPositionPerCharacter(List<String> lines, Chunk a) {
		int index = a.getPosition();
		List<String> sublines = lines.subList(0, index);
		return sumOfCharactersPerCharacter(sublines);
	}

	protected static int chunkLengthPerLine(Chunk a) {
		return sumOfCharactersPerLine((List<String>) a.getLines());
	}

	protected static int chunkLengthPerCharacter(Chunk a) {
		return sumOfCharactersPerCharacter((List<String>) a.getLines());
	}
}
