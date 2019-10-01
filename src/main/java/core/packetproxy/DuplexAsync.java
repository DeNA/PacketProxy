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

import java.io.InputStream;
import java.io.OutputStream;
import packetproxy.common.Endpoint;
import org.apache.commons.lang3.ArrayUtils;

public class DuplexAsync extends Duplex
{
	private Endpoint client;
	private Endpoint server;
	private Simplex client_to_server;
	private Simplex server_to_client;

	public DuplexAsync(Endpoint client_endpoint, Endpoint server_endpoint) throws Exception
	{
		this.client = client_endpoint;
		this.server = server_endpoint;
		InputStream client_input  = (client_endpoint != null) ? client_endpoint.getInputStream() : null;
		OutputStream client_output = (client_endpoint != null) ? client_endpoint.getOutputStream() : null;
		InputStream server_input  = (server_endpoint != null) ? server_endpoint.getInputStream() : null;
		OutputStream server_output = (server_endpoint != null) ? server_endpoint.getOutputStream() : null;

		client_to_server = createClientToServerSimplex(client_input, server_output);
		server_to_client = createServerToClientSimplex(server_input, client_output);
		disableDuplexEventListener();
	}

	@Override
	public Duplex crateSameConnectionDuplex() throws Exception {
		return new DuplexAsync(this.client, this.server);
	}
	public byte[] prepareFastSend(byte[] data) throws Exception {
		int accepted_length = callOnClientPacketReceived(data);
		if (accepted_length <= 0){
			return null;
		}
		byte[] accepted = ArrayUtils.subarray(data, 0, accepted_length);
		byte[] decoded = callOnClientChunkReceived(accepted);
		byte[] encoded = callOnClientChunkSend(decoded);
		return encoded;
	}
	public void execFastSend(byte[] data) throws Exception {
		client_to_server.sendWithoutRecording(data);
	}

	public void start() throws Exception {
		client_to_server.start();
		server_to_client.start();
	}
	@Override
	public void close() throws Exception {
		client_to_server.close();
		server_to_client.close();
	}
	private Simplex createClientToServerSimplex(final InputStream in, final OutputStream out) throws Exception
	{
		Simplex simplex = new Simplex(in, out);
		simplex.addSimplexEventListener(new Simplex.SimplexEventListener() {
			@Override
			public byte[] onChunkReceived(byte[] data) throws Exception {
				return callOnClientChunkReceived(data);
			}
			@Override
			public int onPacketReceived(byte[] data) throws Exception {
				return callOnClientPacketReceived(data);
			}
			@Override
			public byte[] onChunkSend(byte[] data) throws Exception {
				return callOnClientChunkSend(data);
			}
		});
		return simplex;
	}
	private Simplex createServerToClientSimplex(final InputStream in, final OutputStream out) throws Exception
	{
		Simplex simplex = new Simplex(in, out);
		simplex.addSimplexEventListener(new Simplex.SimplexEventListener() {
			@Override
			public byte[] onChunkReceived(byte[] data) throws Exception {
				return callOnServerChunkReceived(data);
			}
			@Override
			public int onPacketReceived(byte[] data) throws Exception {
				return callOnServerPacketReceived(data);
			}
			@Override
			public byte[] onChunkSend(byte[] data) throws Exception {
				return callOnServerChunkSend(data);
			}
		});
		return simplex;
	}
	@Override
	protected void sendToClientImpl(byte[] data) throws Exception {
		server_to_client.sendWithoutRecording(data);
	}
	@Override
	protected void sendToServerImpl(byte[] data) throws Exception {
		client_to_server.sendWithoutRecording(data);
	}
}
