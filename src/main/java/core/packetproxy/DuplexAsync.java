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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Endpoint;

public class DuplexAsync extends Duplex {

	private Endpoint client;
	private Endpoint server;
	private Simplex client_to_server;
	private Simplex server_to_client;
	private Thread clientFlowSourceThread;
	private Thread serverFlowSourceThread;
	private Thread clientFlowSinkThread;
	private Thread serverFlowSinkThread;
	private InputStream client_input;
	private InputStream server_input;
	private OutputStream client_output;
	private OutputStream server_output;
	private PipedInputStream flow_controlled_client_input;
	private PipedOutputStream flow_controlled_client_output;
	private PipedInputStream flow_controlled_server_input;
	private PipedOutputStream flow_controlled_server_output;

	public DuplexAsync(Endpoint client_endpoint, Endpoint server_endpoint) throws Exception {
		this.client = client_endpoint;
		this.server = server_endpoint;
		client_input = (client_endpoint != null) ? client_endpoint.getInputStream() : null;
		client_output = (client_endpoint != null) ? client_endpoint.getOutputStream() : null;
		server_input = (server_endpoint != null) ? server_endpoint.getInputStream() : null;
		server_output = (server_endpoint != null) ? server_endpoint.getOutputStream() : null;

		flow_controlled_client_output = new PipedOutputStream();
		flow_controlled_client_input = new PipedInputStream(flow_controlled_client_output, 65536);

		flow_controlled_server_output = new PipedOutputStream();
		flow_controlled_server_input = new PipedInputStream(flow_controlled_server_output, 65536);

		client_to_server = createClientToServerSimplex(client_input, flow_controlled_server_output);
		server_to_client = createServerToClientSimplex(server_input, flow_controlled_client_output);

		disableDuplexEventListener();
	}

	@Override
	public boolean isListenPort(int listenPort) {
		return this.client.getLocalPort() == listenPort;
	}

	@Override
	public Duplex createSameConnectionDuplex() throws Exception {
		return new DuplexAsync(this.client, this.server);
	}

	public byte[] prepareFastSend(byte[] data) throws Exception {
		int accepted_length = callOnClientPacketReceived(data);
		if (accepted_length <= 0) {

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
		clientFlowSourceThread = new Thread(new Runnable() {

			public void run() {
				try {

					byte[] inputBuf = new byte[65536];
					int inputLen = 0;
					while ((inputLen = flow_controlled_client_input.read(inputBuf)) > 0) {

						callOnClientChunkFlowControl(ArrayUtils.subarray(inputBuf, 0, inputLen));
					}
					flow_controlled_client_input.close();
					closeOnClientChunkFlowControl();
				} catch (Exception e) {

					// e.printStackTrace();
				}
			}
		});

		serverFlowSourceThread = new Thread(new Runnable() {

			public void run() {
				try {

					byte[] inputBuf = new byte[65536];
					int inputLen = 0;
					while ((inputLen = flow_controlled_server_input.read(inputBuf)) > 0) {

						callOnServerChunkFlowControl(ArrayUtils.subarray(inputBuf, 0, inputLen));
					}
					flow_controlled_server_input.close();
					closeOnServerChunkFlowControl();
				} catch (Exception e) {

					// e.printStackTrace();
				}
			}
		});

		clientFlowSinkThread = new Thread(new Runnable() {

			public void run() {
				try {

					byte[] inputBuf = new byte[65536];
					int inputLen = 0;
					while ((inputLen = getClientChunkFlowControlSink().read(inputBuf)) > 0) {

						client_output.write(inputBuf, 0, inputLen);
						client_output.flush();
					}
					flow_controlled_client_input.close();
					client_output.close();
				} catch (Exception e) {

					try {

						flow_controlled_client_input.close();
						client_output.close();
					} catch (Exception e1) {

						// e1.printStackTrace();
					}
					// e.printStackTrace();
				}
			}
		});

		serverFlowSinkThread = new Thread(new Runnable() {

			public void run() {
				try {

					byte[] inputBuf = new byte[65536];
					int inputLen = 0;
					while ((inputLen = getServerChunkFlowControlSink().read(inputBuf)) > 0) {

						server_output.write(inputBuf, 0, inputLen);
						server_output.flush();
					}
					flow_controlled_server_input.close();
					server_output.close();
				} catch (Exception e) {

					try {

						flow_controlled_server_input.close();
						server_output.close();
					} catch (Exception e1) {

						// e1.printStackTrace();
					}
					// e.printStackTrace();
				}
			}
		});

		client_to_server.start();
		server_to_client.start();
		clientFlowSinkThread.start();
		serverFlowSinkThread.start();
		clientFlowSourceThread.start();
		serverFlowSourceThread.start();
	}

	@Override
	public void close() throws Exception {
		client_to_server.close();
		server_to_client.close();
		client_input.close();
		server_output.close();
		server_input.close();
		client_output.close();
	}

	private Simplex createClientToServerSimplex(final InputStream in, final OutputStream out) throws Exception {
		Simplex simplex = new Simplex(in, out);
		simplex.addSimplexEventListener(new Simplex.SimplexEventListener() {

			@Override
			public void onChunkArrived(byte[] data) throws Exception {
				callOnClientChunkArrived(data);
			}

			@Override
			public byte[] onChunkPassThrough() throws Exception {
				return callOnClientChunkPassThrough();
			}

			@Override
			public byte[] onChunkAvailable() throws Exception {
				return callOnClientChunkAvailable();
			}

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

	private Simplex createServerToClientSimplex(final InputStream in, final OutputStream out) throws Exception {
		Simplex simplex = new Simplex(in, out);
		simplex.addSimplexEventListener(new Simplex.SimplexEventListener() {

			@Override
			public byte[] onChunkReceived(byte[] data) throws Exception {
				return callOnServerChunkReceived(data);
			}

			@Override
			public void onChunkArrived(byte[] data) throws Exception {
				callOnServerChunkArrived(data);
			}

			@Override
			public byte[] onChunkPassThrough() throws Exception {
				return callOnServerChunkPassThrough();
			}

			@Override
			public byte[] onChunkAvailable() throws Exception {
				return callOnServerChunkAvailable();
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
