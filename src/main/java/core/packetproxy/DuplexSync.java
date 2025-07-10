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
package packetproxy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Endpoint;
import packetproxy.model.OneShotPacket;

public class DuplexSync extends Duplex {

	private Endpoint server;
	private OutputStream out;
	private InputStream in;

	public DuplexSync(Endpoint endpoint) throws Exception {
		this.server = endpoint;
		this.out = this.server.getOutputStream();
		this.in = this.server.getInputStream();
	}

	public static OneShotPacket encodePacket(OneShotPacket one_shot) {
		return one_shot;
	}

	@Override
	public boolean isListenPort(int listenPort) {
		return false;
	}

	@Override
	public Duplex createSameConnectionDuplex() throws Exception {
		return new DuplexSync(this.server);
	}

	public byte[] prepareFastSend(byte[] data) throws Exception {
		int accepted_length = callOnClientPacketReceived(data);
		if (accepted_length <= 0) {

			return null;
		}
		byte[] accepted = ArrayUtils.subarray(data, 0, accepted_length);

		byte[] pass = callOnClientChunkPassThrough();

		byte[] decoded = super.callOnClientChunkReceived(accepted);
		byte[] encoded = super.callOnClientChunkSend(decoded);

		return ArrayUtils.addAll(pass, encoded);
	}

	public void execFastSend(byte[] data) throws Exception {
		out.write(data);
		out.flush();
	}

	@Override
	public void send(byte[] data) throws Exception {
		int accepted_length = callOnClientPacketReceived(data);
		if (accepted_length <= 0) {

			return;
		}
		byte[] accepted = ArrayUtils.subarray(data, 0, accepted_length);

		byte[] decoded = super.callOnClientChunkReceived(accepted);
		byte[] encoded = super.callOnClientChunkSend(decoded);
		out.write(encoded);
		out.flush();
	}

	@Override
	public byte[] receive() throws Exception {
		byte[] input_data = new byte[100 * 1024];

		ByteArrayOutputStream bin = new ByteArrayOutputStream();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();

		int length = 0;
		while ((length = in.read(input_data, 0, input_data.length)) >= 0) {

			bin.write(input_data, 0, length);

			int packetLen = 0;
			while ((packetLen = callOnServerPacketReceived(bin.toByteArray())) > 0) {

				byte[] packetData = ArrayUtils.subarray(bin.toByteArray(), 0, packetLen);
				byte[] restData = ArrayUtils.subarray(bin.toByteArray(), packetLen, bin.size());
				bin.reset();
				bin.write(restData);

				callOnServerChunkArrived(packetData);

				byte[] available_data = callOnServerChunkAvailable();
				if (available_data == null || available_data.length == 0) {

					continue;
				}
				do {

					byte[] decoded = callOnServerChunkReceived(available_data);
					bout.write(decoded);
					available_data = callOnServerChunkAvailable();
				} while (available_data != null && available_data.length > 0);
				byte[] encoded = callOnServerChunkSend(bout.toByteArray());
				return encoded;
			}
		}
		return null;
	}

	@Override
	public void close() throws Exception {
		in.close();
		out.close();
	}
}
