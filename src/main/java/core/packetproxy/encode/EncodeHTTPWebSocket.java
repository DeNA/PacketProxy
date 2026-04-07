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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import packetproxy.http.Http;
import packetproxy.model.Packet;
import packetproxy.websocket.OpCode;
import packetproxy.websocket.WebSocket;
import packetproxy.websocket.WebSocketFrame;

public class EncodeHTTPWebSocket extends Encoder {

	/**
	 * Sentinel shown in History/Intercept for empty-payload WebSocket frames.
	 * Encode path restores this to a zero-length payload so the wire frame stays
	 * spec-compliant. If the user replaces this text in Intercept, the edited bytes
	 * are sent as the actual payload.
	 */
	static final byte[] EMPTY_PAYLOAD_PLACEHOLDER = "(empty WebSocket frame)".getBytes(StandardCharsets.UTF_8);

	private static boolean isEmptyPlaceholder(byte[] data) {
		return Arrays.equals(data, EMPTY_PAYLOAD_PLACEHOLDER);
	}

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

			byte[] payload = clientWebSocket.frameAvailable();
			// Simplex treats byte[0] from clientRequestAvailable as "no more chunks" (same
			// as Encoder
			// base).
			// Map empty WebSocket payload to the placeholder so the duplex pipeline runs
			// decode/intercept/send.
			if (payload != null && payload.length == 0) {

				return EMPTY_PAYLOAD_PLACEHOLDER;
			}
			return payload;
		} else {

			return super.clientRequestAvailable();
		}
	}

	@Override
	public byte[] serverResponseAvailable() throws Exception {
		if (binary_start) {

			byte[] payload = serverWebSocket.frameAvailable();
			if (payload != null && payload.length == 0) {

				return EMPTY_PAYLOAD_PLACEHOLDER;
			}
			return payload;
		} else {

			return super.serverResponseAvailable();
		}
	}

	@Override
	public byte[] decodeServerResponse(byte[] input) throws Exception {
		if (binary_start) {

			if (input.length == 0) {
				return EMPTY_PAYLOAD_PLACEHOLDER;
			}
			return decodeWebsocketResponse(input);
		} else {

			Http http = Http.create(input);
			return http.toByteArray();
		}
	}

	@Override
	public byte[] encodeServerResponse(byte[] input) throws Exception {
		if (binary_start) {

			// Restore empty payload when the user left the placeholder unchanged.
			boolean emptyPlaceholder = isEmptyPlaceholder(input);
			byte[] payload = emptyPlaceholder ? new byte[0] : encodeWebsocketResponse(input);
			WebSocketFrame frame = WebSocketFrame.of(serverWebSocket.lastDequeuedOpCode(), payload, false);
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

			if (input.length == 0) {
				return EMPTY_PAYLOAD_PLACEHOLDER;
			}
			return decodeWebsocketRequest(input);
		} else {

			Http http = Http.create(input);
			return http.toByteArray();
		}
	}

	@Override
	public byte[] encodeClientRequest(byte[] input) throws Exception {
		if (binary_start) {

			// Restore empty payload when the user left the placeholder unchanged.
			boolean emptyPlaceholder = isEmptyPlaceholder(input);
			byte[] payload = emptyPlaceholder ? new byte[0] : encodeWebsocketRequest(input);
			WebSocketFrame frame = WebSocketFrame.of(clientWebSocket.lastDequeuedOpCode(), payload, true);
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

	@Override
	public String getSummarizedRequest(Packet packet) {
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) {

			return "";
		}
		byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
		// Empty-payload frames use this sentinel in decoded data; Http.create() can
		// still
		// "parse" it into a bogus request line, so WebSocket summary must not go
		// through HTTP.
		if (isEmptyPlaceholder(data)) {

			return summarizeWebSocketClientRequest(packet, data);
		}
		try {

			Http http = Http.create(data);
			String method = http.getMethod();
			if (method != null && !method.isEmpty()) {

				return http.getMethod() + " " + http.getURL(packet.getServerPort(), packet.getUseSSL());
			}
		} catch (Exception ignored) {
			// Upgrade 後の decoded は HTTP メッセージではなく WebSocket の payload になる。
			// そのため Http.create が失敗したら、HTTP ではなく WebSocket とみなして要約する。
		}
		return summarizeWebSocketClientRequest(packet, data);
	}

	private static String summarizeWebSocketClientRequest(Packet packet, byte[] decodedPayload) {
		byte[] raw = packet.getReceivedData();
		if (raw.length == 0) {

			return "WebSocket (" + payloadLengthForSummary(decodedPayload) + " bytes)";
		}
		try {

			WebSocketFrame frame = WebSocketFrame.parse(raw);
			OpCode op = frame.getOpcode();
			byte[] payload = frame.getPayload();
			int n = payload == null ? 0 : payload.length;
			if (isEmptyPlaceholder(decodedPayload)) {

				n = 0;
			}
			if (op == OpCode.Text) {

				return "WebSocket Text (" + n + " bytes)";
			}
			if (op == OpCode.Binary) {

				return "WebSocket Binary (" + n + " bytes)";
			}
			return "WebSocket (" + n + " bytes)";
		} catch (Exception e) {

			return "WebSocket (" + payloadLengthForSummary(decodedPayload) + " bytes)";
		}
	}

	private static int payloadLengthForSummary(byte[] decodedPayload) {
		if (isEmptyPlaceholder(decodedPayload)) {

			return 0;
		}
		return decodedPayload.length;
	}
}
