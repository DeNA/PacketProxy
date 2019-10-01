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

import java.util.Observable;

public class InterceptModel extends Observable {

	private static InterceptModel instance;
	
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
	public void enableInterceptMode() {
		this.intercept_mode = true;
		notifyObservers();
	}
	public void disableInterceptMode() {
		this.intercept_mode = false;
		notifyObservers();
	}
	public boolean isInterceptEnabled() {
		return this.intercept_mode;
	}
	public void setData(byte[] data, Packet client_packet, Packet server_packet) {
		this.data = data;
		this.client_packet = client_packet;
		this.server_packet = server_packet;
		notifyObservers();
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
		clear();
		notifyObservers();
	}
	@Override
	public void notifyObservers(Object arg) {
		setChanged();
		super.notifyObservers(null);
		clearChanged();
	}

	private void clear() {
		this.data = null;
		this.client_packet = null;
		this.server_packet = null;
	}
}
