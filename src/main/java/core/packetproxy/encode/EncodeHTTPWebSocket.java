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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;
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

	/**
	 * Splits {@code 101 Switching Protocols} at the end of HTTP headers so the
	 * first WebSocket frame is not consumed as an HTTP body (some servers set
	 * Content-Length on 101 and append WebSocket bytes in the same TCP read).
	 */
	@Override
	public int checkResponseDelimiter(byte[] input) throws Exception {
		if (binary_start) {

			return WebSocket.checkDelimiter(input);
		}
		int headerSize = HttpHeader.calcHeaderSize(input);
		if (headerSize > 0 && isSwitchingProtocols101(input, headerSize)) {

			return headerSize;
		}
		return Http.parseHttpDelimiter(input);
	}

	private static boolean isSwitchingProtocols101(byte[] data, int headerSize) {
		int lineEnd = 0;
		while (lineEnd < headerSize && lineEnd < data.length && data[lineEnd] != '\n') {

			lineEnd++;
		}
		String firstLine = new String(data, 0, lineEnd, StandardCharsets.UTF_8).trim();
		String[] parts = firstLine.split("\\s+");
		return parts.length >= 2 && parts[0].startsWith("HTTP/") && "101".equals(parts[1]);
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
			WebSocketFrame frame = WebSocketFrame.of(serverWebSocket.lastDequeuedOpCode(), payload, false,
					serverWebSocket.lastDequeuedFin());
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
			WebSocketFrame frame = WebSocketFrame.of(clientWebSocket.lastDequeuedOpCode(), payload, true,
					clientWebSocket.lastDequeuedFin());
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

			return summarizeWebSocketFromRaw(packet.getReceivedData(), data);
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
		return summarizeWebSocketFromRaw(packet.getReceivedData(), data);
	}

	@Override
	public String getSummarizedResponse(Packet packet) {
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) {

			return "";
		}
		byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
		if (isEmptyPlaceholder(data)) {

			return summarizeWebSocketFromRaw(packet.getReceivedData(), data);
		}
		try {

			Http http = Http.create(data);
			String statusCode = http.getStatusCode();
			if (statusCode != null && !statusCode.isEmpty()) {

				return statusCode;
			}
		} catch (Exception ignored) {
			// Upgrade 後の decoded は WebSocket payload のため Http として解釈できない。
		}
		return summarizeWebSocketFromRaw(packet.getReceivedData(), data);
	}

	private static String summarizeWebSocketFromRaw(byte[] raw, byte[] decodedPayload) {
		if (raw.length == 0) {

			return "WebSocket (" + payloadLengthForSummary(decodedPayload) + " bytes)";
		}
		try {

			WebSocketFrame frame = WebSocketFrame.parseSingleFrame(ByteBuffer.wrap(raw));
			OpCode op = frame.getOpcode();
			byte[] payload = frame.getPayload();
			int n = payload == null ? 0 : payload.length;
			if (isEmptyPlaceholder(decodedPayload)) {

				n = 0;
			}
			return formatWebSocketSummary(op, frame.isFin(), n);
		} catch (Exception e) {

			return "WebSocket (" + payloadLengthForSummary(decodedPayload) + " bytes)";
		}
	}

	private static String formatWebSocketSummary(OpCode op, boolean fin, int n) {
		if (op == null) {

			return "WebSocket (" + n + " bytes)";
		}
		if (op == OpCode.Text) {

			return (fin ? "WebSocket Text" : "WebSocket Text fragment") + " (" + n + " bytes)";
		}
		if (op == OpCode.Binary) {

			return (fin ? "WebSocket Binary" : "WebSocket Binary fragment") + " (" + n + " bytes)";
		}
		if (op == OpCode.Cont) {

			return "WebSocket Continuation (" + n + " bytes)";
		}
		if (op == OpCode.Ping) {

			return "WebSocket Ping (" + n + " bytes)";
		}
		if (op == OpCode.Pong) {

			return "WebSocket Pong (" + n + " bytes)";
		}
		if (op == OpCode.Close) {

			return "WebSocket Close (" + n + " bytes)";
		}
		if (op == OpCode.DataRsv1 || op == OpCode.DataRsv2 || op == OpCode.DataRsv3 || op == OpCode.DataRsv4
				|| op == OpCode.DataRsv5) {

			return "WebSocket Data(reserved) (" + n + " bytes)";
		}
		if (op == OpCode.CtrlRsv1 || op == OpCode.CtrlRsv2 || op == OpCode.CtrlRsv3 || op == OpCode.CtrlRsv4
				|| op == OpCode.CtrlRsv5) {

			return "WebSocket Ctrl(reserved) (" + n + " bytes)";
		}
		return "WebSocket (" + n + " bytes)";
	}

	private static int payloadLengthForSummary(byte[] decodedPayload) {
		if (isEmptyPlaceholder(decodedPayload)) {

			return 0;
		}
		return decodedPayload.length;
	}
}
