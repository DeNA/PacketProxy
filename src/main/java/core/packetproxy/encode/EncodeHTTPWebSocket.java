/*
 * Copyright 2019,2023 DeNA Co., Ltd.
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
package packetproxy.encode;

import packetproxy.http.Http;
import packetproxy.websocket.WebSocket;
import packetproxy.websocket.WebSocketFrame;

public class EncodeHTTPWebSocket extends Encoder {

	protected boolean binary_start = false;
	WebSocket clientWebSocket = new WebSocket();
	WebSocket serverWebSocket = new WebSocket();

	public EncodeHTTPWebSocket(String ALPN) throws Exception {
		super(ALPN);
	}

	public EncodeHTTPWebSocket() throws Exception {
		super();
	}

	@Override
	public boolean useNewConnectionForResend() {
		return false;
	}

	@Override
	public boolean useNewEncoderForResend() {
		return false;
	}

	@Override
	public String getName() {
		return "HTTP WebSocket";
	}

	@Override
	public int checkDelimiter(byte[] input) throws Exception {
		if (binary_start) {

			return WebSocket.checkDelimiter(input);
		} else {

			return Http.parseHttpDelimiter(input);
		}
	}

	@Override
	public void clientRequestArrived(byte[] input) throws Exception {
		if (binary_start) {

			clientWebSocket.frameArrived(input);
		} else {

			super.clientRequestArrived(input);
		}
	}

	@Override
	public void serverResponseArrived(byte[] input) throws Exception {
		if (binary_start) {

			serverWebSocket.frameArrived(input);
		} else {

			super.serverResponseArrived(input);
		}
	}

	@Override
	public byte[] passThroughClientRequest() throws Exception {
		if (binary_start) {

			return clientWebSocket.passThroughFrame();
		} else {

			return super.passThroughClientRequest();
		}
	}

	@Override
	public byte[] passThroughServerResponse() throws Exception {
		if (binary_start) {

			return serverWebSocket.passThroughFrame();
		} else {

			return super.passThroughServerResponse();
		}
	}

	@Override
	public byte[] clientRequestAvailable() throws Exception {
		if (binary_start) {

			return clientWebSocket.frameAvailable();
		} else {

			return super.clientRequestAvailable();
		}
	}

	@Override
	public byte[] serverResponseAvailable() throws Exception {
		if (binary_start) {

			return serverWebSocket.frameAvailable();
		} else {

			return super.serverResponseAvailable();
		}
	}

	@Override
	public byte[] decodeServerResponse(byte[] input) throws Exception {
		if (binary_start) {

			return decodeWebsocketResponse(input);
		} else {

			Http http = Http.create(input);
			return http.toByteArray();
		}
	}

	@Override
	public byte[] encodeServerResponse(byte[] input) throws Exception {
		if (binary_start) {

			byte[] payload = encodeWebsocketResponse(input);
			WebSocketFrame frame = WebSocketFrame.of(payload, false);
			return frame.getBytes();
		} else {

			Http http = Http.create(input);
			// encodeでやらないと、Switching Protocolsのレスポンス自体がwebsocketとしてencodeされてしまう
			binary_start = http.getStatusCode().matches("101");
			return http.toByteArray();
		}
	}

	@Override
	public byte[] decodeClientRequest(byte[] input) throws Exception {
		if (binary_start) {

			return decodeWebsocketRequest(input);
		} else {

			Http http = Http.create(input);
			return http.toByteArray();
		}
	}

	@Override
	public byte[] encodeClientRequest(byte[] input) throws Exception {
		if (binary_start) {

			byte[] payload = encodeWebsocketRequest(input);
			WebSocketFrame frame = WebSocketFrame.of(payload, true);
			return frame.getBytes();
		} else {

			Http http = Http.create(input);
			return http.toByteArray();
		}
	}

	@Override
	public String getContentType(byte[] input) throws Exception {
		if (binary_start) {

			return "WebSocket";
		} else {

			Http http = Http.create(input);
			return http.getFirstHeader("Content-Type");
		}
	}

	public byte[] decodeWebsocketRequest(byte[] input) throws Exception {
		return input;
	}

	public byte[] encodeWebsocketRequest(byte[] input) throws Exception {
		return input;
	}

	public byte[] decodeWebsocketResponse(byte[] input) throws Exception {
		return input;
	}

	public byte[] encodeWebsocketResponse(byte[] input) throws Exception {
		return input;
	}
}
