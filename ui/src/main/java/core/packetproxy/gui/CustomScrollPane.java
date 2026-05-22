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

import java.awt.Component;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

public class CustomScrollPane extends JScrollPane {

	public CustomScrollPane() {
		super();
		addMouseWheelListener(new CustomMouseWheelListener());
	}

	class CustomMouseWheelListener implements MouseWheelListener {

		private JScrollBar bar;
		private int previousValue = 0;
		private JScrollPane parentScrollPane;

		private JScrollPane getParentScrollPane() {
			if (parentScrollPane == null) {

				Component parent = getParent();
				while (!(parent instanceof JScrollPane) && parent != null) {

					parent = parent.getParent();
				}
				parentScrollPane = (JScrollPane) parent;
			}
			return parentScrollPane;
		}

		public CustomMouseWheelListener() {
			bar = CustomScrollPane.this.getVerticalScrollBar();
		}

		public void mouseWheelMoved(MouseWheelEvent e) {
			JScrollPane parent = getParentScrollPane();
			if (parent != null) {

				if (e.getWheelRotation() < 0) {

					if (bar.getValue() == 0 && previousValue == 0) {

						parent.dispatchEvent(cloneEvent(e));
					}
				} else {

					if (bar.getValue() == getMax() && previousValue == getMax()) {

						parent.dispatchEvent(cloneEvent(e));
					}
				}
				previousValue = bar.getValue();
			} else {

				CustomScrollPane.this.removeMouseWheelListener(this);
			}
		}

		private int getMax() {
			return bar.getMaximum() - bar.getVisibleAmount();
		}

		private MouseWheelEvent cloneEvent(MouseWheelEvent e) {
			return new MouseWheelEvent(getParentScrollPane(), e.getID(), e.getWhen(), e.getModifiers(), 1, 1,
					e.getClickCount(), false, e.getScrollType(), e.getScrollAmount(), e.getWheelRotation());
		}
	}
}
