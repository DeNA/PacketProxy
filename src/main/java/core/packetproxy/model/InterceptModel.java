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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import static packetproxy.model.PropertyChangeEventType.INTERCEPT_DATA;
import static packetproxy.model.PropertyChangeEventType.INTERCEPT_MODE;

public class InterceptModel {

	private static InterceptModel instance;
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public static InterceptModel getInstance() throws Exception {
		if (instance == null) {
			instance = new InterceptModel();
		}
		return instance;
	}

	private byte[] data;
	private Packet client_packet;
	private Packet server_packet;
	private boolean intercept_mode = false;

	private InterceptModel() {
		clear();
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public void enableInterceptMode() {
		boolean oldValue = this.intercept_mode;
		this.intercept_mode = true;
		pcs.firePropertyChange(INTERCEPT_MODE.toString(), oldValue, this.intercept_mode);
	}

	public void disableInterceptMode() {
		boolean oldValue = this.intercept_mode;
		this.intercept_mode = false;
		pcs.firePropertyChange(INTERCEPT_MODE.toString(), oldValue, this.intercept_mode);
	}

	public boolean isInterceptEnabled() {
		return this.intercept_mode;
	}

	public void setData(byte[] data, Packet client_packet, Packet server_packet) {
		byte[] oldData = this.data;
		this.data = data;
		this.client_packet = client_packet;
		this.server_packet = server_packet;
		pcs.firePropertyChange(INTERCEPT_DATA.toString(), oldData, this.data);
	}

	public byte[] getData() {
		return data;
	}

	public Packet getClientPacket() {
		return this.client_packet;
	}

	public Packet getServerPacket() {
		return this.server_packet;
	}

	public void clearData() {
		byte[] oldData = this.data;
		clear();
		pcs.firePropertyChange(INTERCEPT_DATA.toString(), oldData, this.data);
	}

	private void clear() {
		this.data = null;
		this.client_packet = null;
		this.server_packet = null;
	}
}
