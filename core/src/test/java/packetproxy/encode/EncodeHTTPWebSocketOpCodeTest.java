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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import packetproxy.websocket.OpCode;
import packetproxy.websocket.WebSocket;
import packetproxy.websocket.WebSocketFrame;

/**
 * Tests that: 1. Text vs Binary opcode is preserved across decode/encode. 2.
 * Empty-payload WebSocket frames flow through the pipeline.
 */
public class EncodeHTTPWebSocketOpCodeTest {

	/** RFC 6455 frame byte 0: FIN (bit 7). */
	private static final int WS_FIN_BIT = 0x80;

	/** RFC 6455 frame byte 1: MASK (bit 7) for client-to-server frames. */
	private static final int WS_MASK_BIT = 0x80;

	/**
	 * RFC 6455 frame byte 1: bits 0–6 — payload length when that value is 0–125.
	 */
	private static final int WS_PAYLOAD_LEN_7BIT_MASK = 0x7F;

	private static byte finFirstByte(OpCode opcode) {
		return (byte) (WS_FIN_BIT | (opcode.code & 0x0F));
	}

	private static byte unmaskedPayloadLenByte(int payloadLength) {
		return (byte) (payloadLength & WS_PAYLOAD_LEN_7BIT_MASK);
	}

	private static byte maskedPayloadLenByte(int payloadLength) {
		return (byte) (WS_MASK_BIT | (payloadLength & WS_PAYLOAD_LEN_7BIT_MASK));
	}

	private static int payloadLen7Bits(byte secondByte) {
		return secondByte & WS_PAYLOAD_LEN_7BIT_MASK;
	}

	/** Unmasked FIN+Text frame, payload "hello". */
	private static byte[] textFrameHello() {
		byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
		byte[] frame = new byte[2 + payload.length];
		frame[0] = finFirstByte(OpCode.Text);
		frame[1] = unmaskedPayloadLenByte(payload.length);
		System.arraycopy(payload, 0, frame, 2, payload.length);
		return frame;
	}

	/** Unmasked FIN+Binary frame, single zero byte payload. */
	private static byte[] binaryFrameOneByte() {
		return new byte[]{finFirstByte(OpCode.Binary), unmaskedPayloadLenByte(1), 0x00};
	}

	@Test
	public void frameAvailableRecordsTextOpcode() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(textFrameHello());
		assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ws.frameAvailable());
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
	public void encodeClientRequestPreservesTextOpcode() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(textFrameHello());
		byte[] payload = encoder.clientRequestAvailable();
		byte[] wire = encoder.encodeClientRequest(payload);
		assertEquals(finFirstByte(OpCode.Text), wire[0]);
	}

	@Test
	public void encodeClientRequestPreservesBinaryOpcode() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(binaryFrameOneByte());
		byte[] payload = encoder.clientRequestAvailable();
		byte[] wire = encoder.encodeClientRequest(payload);
		assertEquals(finFirstByte(OpCode.Binary), wire[0]);
	}

	@Test
	public void encodeServerResponsePreservesTextOpcode() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.serverWebSocket.frameArrived(textFrameHello());
		byte[] payload = encoder.serverResponseAvailable();
		byte[] wire = encoder.encodeServerResponse(payload);
		assertEquals(finFirstByte(OpCode.Text), wire[0]);
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

	/** Unmasked FIN+Text frame, zero-length payload. */
	private static byte[] textFrameEmptyPayload() {
		return new byte[]{finFirstByte(OpCode.Text), unmaskedPayloadLenByte(0)};
	}

	/** Unmasked FIN+Binary frame, zero-length payload. */
	private static byte[] binaryFrameEmptyPayload() {
		return new byte[]{finFirstByte(OpCode.Binary), unmaskedPayloadLenByte(0)};
	}

	@Test
	public void emptyPayloadTextFrameQueuesForDecode() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(textFrameEmptyPayload());
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
		assertArrayEquals(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER, payload);
		byte[] decoded = encoder.decodeClientRequest(payload);
		byte[] wire = encoder.encodeClientRequest(decoded);
		// lastDequeuedOpCode preserves Text from the original frame
		assertEquals(finFirstByte(OpCode.Text), wire[0]);
		assertEquals(0, payloadLen7Bits(wire[1]));
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
		assertArrayEquals(binaryFrameEmptyPayload(), wire);
	}

	@Test
	public void editedPlaceholderClientRequestSendsNewContent() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(textFrameEmptyPayload());
		encoder.clientWebSocket.passThroughFrame();
		encoder.clientRequestAvailable();
		byte[] userEdited = "hello".getBytes(StandardCharsets.UTF_8);
		byte[] wire = encoder.encodeClientRequest(userEdited);
		// lastDequeuedOpCode preserves Text from the original frame
		assertEquals(finFirstByte(OpCode.Text), wire[0]);
		assertEquals(maskedPayloadLenByte(userEdited.length), wire[1]);
	}

	/**
	 * If the literal placeholder bytes are sent as payload without having come from
	 * an empty frame, encode must not collapse them to a zero-length payload.
	 */
	@Test
	public void placeholderPayloadWithoutEmptyFrameFlagIsNotCollapsedToEmpty() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.clientWebSocket.frameArrived(textFrameHello());
		encoder.clientWebSocket.passThroughFrame();
		encoder.clientRequestAvailable();
		byte[] wire = encoder.encodeClientRequest(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER);
		assertEquals(finFirstByte(OpCode.Text), wire[0]);
		assertNotEquals(0, payloadLen7Bits(wire[1]));
	}

	@Test
	public void placeholderServerPayloadWithoutEmptyFrameFlagIsNotCollapsedToEmpty() throws Exception {
		TestEncoder encoder = new TestEncoder();
		encoder.setBinaryStart(true);
		encoder.serverWebSocket.frameArrived(textFrameHello());
		encoder.serverWebSocket.passThroughFrame();
		encoder.serverResponseAvailable();
		byte[] wire = encoder.encodeServerResponse(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER);
		assertEquals(finFirstByte(OpCode.Text), wire[0]);
		assertEquals(EncodeHTTPWebSocket.EMPTY_PAYLOAD_PLACEHOLDER.length, payloadLen7Bits(wire[1]));
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
