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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import packetproxy.model.Packet;
import packetproxy.websocket.OpCode;
import packetproxy.websocket.WebSocket;
import packetproxy.websocket.WebSocketFrame;

/**
 * Ensures Text vs Binary opcode is preserved across decode (frameAvailable) and
 * encode (getBytes), and that empty-payload frames flow through the normal
 * decode/encode pipeline so they appear in History and can be intercepted.
 */
public class EncodeHTTPWebSocketOpCodeTest {

	private static final byte FIN_TEXT = (byte) 0x81;
	private static final byte FIN_BINARY = (byte) 0x82;

	/** Unmasked FIN+Text frame, payload "hello". */
	private static byte[] textFrameHello() {
		return new byte[]{FIN_TEXT, 0x05, 'h', 'e', 'l', 'l', 'o'};
	}

	/** Unmasked FIN+Binary frame, single zero byte payload. */
	private static byte[] binaryFrameOneByte() {
		return new byte[]{FIN_BINARY, 0x01, 0x00};
	}

	/** Unmasked FIN+Text frame, zero-length payload. */
	private static byte[] textFrameEmptyPayload() {
		return new byte[]{FIN_TEXT, 0x00};
	}

	/** Unmasked FIN+Binary frame, zero-length payload. */
	private static byte[] binaryFrameEmptyPayload() {
		return new byte[]{FIN_BINARY, 0x00};
	}

	private static Packet clientPacket(byte[] decoded, byte[] received) throws Exception {
		InetSocketAddress client = new InetSocketAddress("127.0.0.1", 12345);
		InetSocketAddress server = new InetSocketAddress("example.com", 80);
		Packet p = new Packet(0, client, server, "example.com", false, "HTTP WebSocket", null, Packet.Direction.CLIENT,
				0, 0L);
		p.setDecodedData(decoded);
		p.setReceivedData(received);
		return p;
	}

	@Test
	public void getSummarizedRequest_httpHandshake_matchesHttpEncoderOneLine() throws Exception {
		byte[] httpBytes = "GET /endpoint HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(StandardCharsets.UTF_8);
		EncodeHTTPWebSocket encoder = new EncodeHTTPWebSocket();
		Packet packet = clientPacket(httpBytes, httpBytes);
		assertEquals("GET http://example.com/endpoint", encoder.getSummarizedRequest(packet));
	}

	@Test
	public void getSummarizedRequest_textFrame_showsKindAndSize() throws Exception {
		byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
		EncodeHTTPWebSocket encoder = new EncodeHTTPWebSocket();
		Packet packet = clientPacket(payload, textFrameHello());
		assertEquals("WebSocket Text (5 bytes)", encoder.getSummarizedRequest(packet));
	}

	@Test
	public void getSummarizedRequest_binaryFrame_showsKindAndSize() throws Exception {
		byte[] payload = new byte[]{0x00};
		EncodeHTTPWebSocket encoder = new EncodeHTTPWebSocket();
		Packet packet = clientPacket(payload, binaryFrameOneByte());
		assertEquals("WebSocket Binary (1 bytes)", encoder.getSummarizedRequest(packet));
	}

	@Test
	public void getSummarizedRequest_emptyTextFrame_showsZeroBytes() throws Exception {
		EncodeHTTPWebSocket encoder = new EncodeHTTPWebSocket();
		Packet packet = clientPacket(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER, textFrameEmptyPayload());
		assertEquals("WebSocket Text (0 bytes)", encoder.getSummarizedRequest(packet));
	}

	@Test
	public void getSummarizedRequest_emptyBinaryFrame_showsZeroBytes() throws Exception {
		EncodeHTTPWebSocket encoder = new EncodeHTTPWebSocket();
		Packet packet = clientPacket(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER, binaryFrameEmptyPayload());
		assertEquals("WebSocket Binary (0 bytes)", encoder.getSummarizedRequest(packet));
	}

	@Test
	public void frameAvailableRecordsTextOpcode() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(textFrameHello());
		assertArrayEquals("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8), ws.frameAvailable());
		assertEquals(OpCode.Text, ws.lastDequeuedOpCode());
	}

	@Test
	public void frameAvailableRecordsBinaryOpcode() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(binaryFrameOneByte());
		assertArrayEquals(new byte[]{0x00}, ws.frameAvailable());
		assertEquals(OpCode.Binary, ws.lastDequeuedOpCode());
	}

	@Test
	public void emptyPayloadTextFrameQueuesForDecode() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(textFrameEmptyPayload());
		// Empty Text/Binary frames must go through the decode/encode pipeline so
		// History can record them and Intercept can edit them.
		assertArrayEquals(new byte[0], ws.passThroughFrame());
		assertArrayEquals(new byte[0], ws.frameAvailable());
	}

	@Test
	public void emptyPayloadBinaryFrameQueuesForDecode() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(binaryFrameEmptyPayload());
		assertArrayEquals(new byte[0], ws.passThroughFrame());
		assertArrayEquals(new byte[0], ws.frameAvailable());
	}

	@Test
	public void decodeClientRequestReturnsPlaceholderForEmptyPayload() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		byte[] decoded = encoder.decodeClientRequest(new byte[0]);
		assertArrayEquals(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER, decoded);
	}

	@Test
	public void decodeServerResponseReturnsPlaceholderForEmptyPayload() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		byte[] decoded = encoder.decodeServerResponse(new byte[0]);
		assertArrayEquals(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER, decoded);
	}

	@Test
	public void emptyPayloadClientRequestRoundTrip() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(textFrameEmptyPayload());
		encoder.clientWebSocket.passThroughFrame();
		byte[] payload = encoder.clientRequestAvailable();
		assertNotNull(payload);
		// Empty wire payload must become the placeholder so Simplex does not treat
		// byte[0] as EOF.
		assertArrayEquals(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER, payload);
		byte[] decoded = encoder.decodeClientRequest(payload);
		byte[] wire = encoder.encodeClientRequest(decoded);
		// Client frames are always masked (WebSocket spec), so the wire bytes differ
		// from the unmasked test frame. Verify the opcode and effective payload length.
		assertEquals(FIN_TEXT, wire[0]);
		assertEquals(0, wire[1] & 0x7F); // payload length bits must be 0
	}

	@Test
	public void emptyPayloadServerResponseRoundTrip() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.serverWebSocket.frameArrived(binaryFrameEmptyPayload());
		encoder.serverWebSocket.passThroughFrame();
		byte[] payload = encoder.serverResponseAvailable();
		assertNotNull(payload);
		assertArrayEquals(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER, payload);
		byte[] decoded = encoder.decodeServerResponse(payload);
		byte[] wire = encoder.encodeServerResponse(decoded);
		// Server frames are unmasked, so the bytes match exactly.
		assertArrayEquals(binaryFrameEmptyPayload(), wire);
	}

	@Test
	public void editedPlaceholderClientRequestSendsNewContent() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(textFrameEmptyPayload());
		encoder.clientWebSocket.passThroughFrame();
		encoder.clientRequestAvailable();
		// Simulate user replacing the placeholder with new content in Intercept.
		byte[] userEdited = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] wire = encoder.encodeClientRequest(userEdited);
		// First byte: FIN + opcode (Text frame).
		assertEquals(FIN_TEXT, wire[0]);
		// Payload length byte should be 5.
		assertEquals((byte) (0x80 | 5), wire[1]);
	}

	@Test
	public void encodeClientRequestPreservesTextOpcode() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(textFrameHello());
		byte[] payload = encoder.clientRequestAvailable();
		byte[] wire = encoder.encodeClientRequest(payload);
		assertEquals(FIN_TEXT, wire[0]);
	}

	@Test
	public void encodeClientRequestPreservesBinaryOpcode() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(binaryFrameOneByte());
		byte[] payload = encoder.clientRequestAvailable();
		byte[] wire = encoder.encodeClientRequest(payload);
		assertEquals(FIN_BINARY, wire[0]);
	}

	@Test
	public void encodeServerResponsePreservesTextOpcode() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.serverWebSocket.frameArrived(textFrameHello());
		byte[] payload = encoder.serverResponseAvailable();
		byte[] wire = encoder.encodeServerResponse(payload);
		assertEquals(FIN_TEXT, wire[0]);
	}

	@Test
	public void parseThenSerializeRoundTripKeepsOpcode() throws Exception {
		WebSocketFrame text = WebSocketFrame.parse(textFrameHello());
		assertEquals(OpCode.Text, text.getOpcode());
		assertArrayEquals(textFrameHello(), WebSocketFrame.of(text.getOpcode(), text.getPayload(), false).getBytes());

		WebSocketFrame bin = WebSocketFrame.parse(binaryFrameOneByte());
		assertEquals(OpCode.Binary, bin.getOpcode());
		assertArrayEquals(binaryFrameOneByte(), WebSocketFrame.of(bin.getOpcode(), bin.getPayload(), false).getBytes());
	}

	private static final class TestEncoder extends EncodeHTTPWebSocket {

		TestEncoder() throws Exception {
			super();
		}

		void setBinaryStart(boolean value) {
			binary_start = value;
		}
	}
}
