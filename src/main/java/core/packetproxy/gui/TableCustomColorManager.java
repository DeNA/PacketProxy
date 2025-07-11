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
import java.util.HashMap;
import java.util.Map;

public class TableCustomColorManager {

	class LineColor {

		private int packetId;
		private Color color;

		public LineColor(int packetId, Color color) {
			this.packetId = packetId;
			this.color = color;
		}

		public int getPacketID() {
			return this.packetId;
		}

		public Color getColor() {
			return this.color;
		}

		@Override
		public String toString() {
			return String.format("{%d:%s}", this.packetId, this.color.toString());
		}
	}

	private Map<Integer, LineColor> coloredLines;

	public TableCustomColorManager() {
		this.coloredLines = new HashMap<Integer, LineColor>();
	}

	public void add(int packetId, Color color) {
		coloredLines.put(packetId, new LineColor(packetId, color));
	}

	public void clear(int packetId) {
		coloredLines.remove(packetId);
	}

	public void clear() {
		coloredLines.clear();
	}

	public boolean contains(int packetId) {
		return coloredLines.containsKey(packetId) ? true : false;
	}

	public Color getColor(int packetId) throws Exception {
		if (coloredLines.containsKey(packetId)) {

			return coloredLines.get(packetId).getColor();
		}
		throw new Exception("line color is not registered.");
	}

	@Override
	public String toString() {
		return "coloredLines: " + coloredLines.toString();
	}
}
