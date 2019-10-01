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
import packetproxy.common.Endpoint;
import packetproxy.model.OneShotPacket;
import org.apache.commons.lang3.ArrayUtils;

public class DuplexSync extends Duplex
{
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
	public Duplex crateSameConnectionDuplex() throws Exception {
		return new DuplexSync(this.server);
	}
	public byte[] prepareFastSend(byte[] data) throws Exception {
		int accepted_length = callOnClientPacketReceived(data);
		if (accepted_length <= 0){
			return null;
		}
		byte[] accepted = ArrayUtils.subarray(data, 0, accepted_length);
		
		byte[] decoded = super.callOnClientChunkReceived(accepted);
		byte[] encoded = super.callOnClientChunkSend(decoded);
		return encoded;
	}
	public void execFastSend(byte[] data) throws Exception {
		out.write(data);
		out.flush();
	}
	
	@Override
	public void send(byte[] data) throws Exception {
		int accepted_length = callOnClientPacketReceived(data);
		if (accepted_length <= 0){
			return;
		}
		byte[] accepted = ArrayUtils.subarray(data, 0, accepted_length);
		
		byte[] decoded = super.callOnClientChunkReceived(accepted);
		byte[] encoded = super.callOnClientChunkSend(decoded);
		out.write(encoded);
		out.flush();
	}

	@Override
	public void sendToServer(byte[] data) throws Exception {
		byte[] encoded = super.callOnClientChunkSend(data);
		out.write(encoded);
		out.flush();
	}
	
	class Recever extends Thread {
		public void run() {
			try {
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				byte[] input_data = new byte[100 * 1024];
				int length = 0;

				while((length = in.read(input_data, 0, input_data.length)) != -1)
				{
					bout.write(input_data, 0, length);
					int accepted_input_size = 0;

					while (bout.size() > 0 && (accepted_input_size = callOnServerPacketReceived(bout.toByteArray())) > 0)
					{
						byte[] accepted_array   = ArrayUtils.subarray(bout.toByteArray(), 0, accepted_input_size);
						byte[] unaccepted_array = ArrayUtils.subarray(bout.toByteArray(), accepted_input_size, bout.size());
						bout.reset();
						bout.write(unaccepted_array);

						callOnServerChunkReceived(accepted_array);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void receiveAll() throws Exception {
		Recever recv = new Recever();
		recv.start();
	}
	
	@Override
	public byte[] receive() throws Exception {
		byte[] input_data = new byte[100 * 1024];
		ByteArrayOutputStream bout = new ByteArrayOutputStream();

		int length = 0;
		while((length = in.read(input_data, 0, input_data.length)) != -1){
			bout.write(input_data, 0, length);
			if (bout.size() > 0 && callOnServerPacketReceived(bout.toByteArray()) > 0){
				break;
			}
		}
			
		byte[] accepted = ArrayUtils.subarray(bout.toByteArray(), 0, bout.size());
		byte[] decoded = super.callOnServerChunkReceived(accepted);
		byte[] encoded = super.callOnServerChunkSend(decoded);
		return encoded;
	}
	
	@Override
	public void close() throws Exception {
		in.close();
		out.close();
	}
}
