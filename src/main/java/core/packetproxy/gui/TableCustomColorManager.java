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

import packetproxy.util.PacketProxyUtility;

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

	private Map<Integer,LineColor> coloredLines;
	private Map<Integer,LineColor> clearedLines;

	public TableCustomColorManager() {
		this.coloredLines = new HashMap<Integer, LineColor>();
		this.clearedLines = new HashMap<Integer, LineColor>();
	}

	public void add(int packetId, Color color) {
		coloredLines.put(packetId, new LineColor(packetId, color));
		clearedLines.remove(packetId);
	}

	public void clear(int packetId) {
		LineColor lc = coloredLines.get(packetId);
		clearedLines.put(packetId, lc);
		coloredLines.remove(packetId);
	}

	public void clear() {
		coloredLines.forEach((key, value) -> {
			clearedLines.putIfAbsent(key, value);
		});
		coloredLines.clear();
	}

	public boolean contains(int packetId) {
		return (coloredLines.containsKey(packetId) || clearedLines.containsKey(packetId)) ? true : false;
	}

	public Color getColor(int packetId) throws Exception {
		if (coloredLines.containsKey(packetId)) {
			return coloredLines.get(packetId).getColor();
		} else if (clearedLines.containsKey(packetId)) {
			return Color.WHITE;
		}
		throw new Exception("line color is not registered.");
	}

	@Override
	public String toString() {
		return "coloredLines: " + coloredLines.toString() + " clearedLines: " + clearedLines.toString();
	}

	/*
	public static void main(String[] args) {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		try {
			TableCustomColorManager manager = new TableCustomColorManager();
			manager.add(1, Color.BLACK);
			manager.add(2, Color.BLUE);
			util.packetProxyLog(manager.toString());
			util.packetProxyLog(manager.getColor(1).toString());
			util.packetProxyLog(manager.getColor(2).toString());
			manager.clear();
			util.packetProxyLog(manager.toString());
			util.packetProxyLog(manager.getColor(1).toString());
			util.packetProxyLog(manager.getColor(2).toString());
			util.packetProxyLog(manager.getColor(1).toString());
			util.packetProxyLog(manager.getColor(2).toString());
			manager.clear();
			util.packetProxyLog(manager.toString());
			util.packetProxyLog(manager.getColor(1).toString());
			util.packetProxyLog(manager.getColor(2).toString());
			manager.add(1, Color.BLACK);
			util.packetProxyLog(manager.toString());
			util.packetProxyLog(manager.getColor(1).toString());
			util.packetProxyLog(manager.getColor(2).toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	*/

}
