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
package packetproxy.controller;

import packetproxy.model.InterceptModel;
import packetproxy.model.InterceptOptions;
import packetproxy.model.Packet;
import packetproxy.model.Server;

public class InterceptController {
	private static InterceptController instance;

	public static InterceptController getInstance() throws Exception {
		if (instance == null) {
			instance = new InterceptController();
		}
		return instance;
	}

	private boolean forward;
	private boolean forward_multiple;
	private byte[] intercepted_data;
	private InterceptModel interceptModel;
	private Object lock;
	private Object thread_lock;
	private ResendController resend_controller;

	private InterceptController() throws Exception {
		this.forward = false;
		this.forward_multiple = false;
		this.interceptModel = InterceptModel.getInstance();
		this.lock = new Object();
		this.thread_lock = new Object();
		this.resend_controller = ResendController.getInstance();
	}
	public void enableInterceptMode() {
		interceptModel.enableInterceptMode();
	}
	public void disableInterceptMode(byte[] data) {
		synchronized (lock) {
			forward = true;
			forward_multiple = false;
			intercepted_data = data;
			interceptModel.disableInterceptMode();
			lock.notify();
		}
	}
	public void forward(byte[] data) {
		synchronized (lock) {
			forward = true;
			forward_multiple = false;
			intercepted_data = data;
			lock.notify();
		}
	}
	public void forward_multiple(byte[] data) {
		synchronized (lock) {
			forward = true;
			forward_multiple = true;
			intercepted_data = data;
			lock.notify();
		}
	}
	public void drop() {
		synchronized (lock) {
			forward = false;
			forward_multiple = false;
			intercepted_data = null;
			lock.notify();
		}
	}
	public byte[] received(byte[] data, Server server, Packet client_packet) throws Exception {
		return received(data, server, client_packet, null);
	}
	public byte[] received(byte[] data, Server server, Packet client_packet, Packet server_packet) throws Exception {
		Packet target_packet = server_packet == null ? client_packet : server_packet;
		synchronized (thread_lock) {
			// Intercept=ON & InterceptOption=ON、かつ、指定されたルールにマッチした場合にのみIntercept
			boolean is_target_packet = interceptModel.isInterceptEnabled();
			if (is_target_packet) {
				if (InterceptOptions.getInstance().isEnabled()) {
					if (server_packet == null) {
						is_target_packet = InterceptOptions.getInstance().interceptOnRequest(server, client_packet);
					} else {
						is_target_packet = InterceptOptions.getInstance().interceptOnResponse(server, client_packet,
								server_packet);
					}
				}
			}
			if (!is_target_packet) {
				return data;
			}

			synchronized (lock) {
				interceptModel.setData(data, client_packet, server_packet);
				lock.wait();
				interceptModel.clearData();
				if (forward == false) { // drop
					return new byte[]{};
				}
				if (forward_multiple == true) {
					// Forward x20(= original x 1 + copy x 19)
					resend_controller.resend(target_packet.getOneShotPacket(intercepted_data), 19, true);
					target_packet.setResend();
				}
				return intercepted_data;
			}
		}
	}
}
