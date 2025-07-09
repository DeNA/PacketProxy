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

import java.util.EventListener;
import javax.swing.JButton;
import javax.swing.JTextPane;
import javax.swing.event.EventListenerList;

abstract class GUIHistoryPanel {
	public abstract JTextPane getTextPane();

	protected EventListenerList listenerList = new EventListenerList();
	public interface DataChangedListener extends EventListener {
		void dataChanged(byte[] data);
	}
	public void addDataChangedListener(DataChangedListener listener) {
		listenerList.add(DataChangedListener.class, listener);
	}
	protected void callDataChanged(byte[] data) {
		for (DataChangedListener listener : listenerList.getListeners(DataChangedListener.class)) {
			listener.dataChanged(data);
		}
	}
	public abstract void setData(byte[] data) throws Exception;
	public abstract byte[] getData();
	public abstract void setParentTabs(TabSet parentTabs);
	public abstract JButton getParentSend();
}
