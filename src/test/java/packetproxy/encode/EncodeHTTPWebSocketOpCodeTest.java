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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import packetproxy.websocket.OpCode;
import packetproxy.websocket.WebSocket;
import packetproxy.websocket.WebSocketFrame;

/**
 * Ensures Text vs Binary opcode is preserved across decode (frameAvailable) and
 * encode (getBytes).
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
	public void emptyPayloadTextFrameIsPassedThrough() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(textFrameEmptyPayload());
		assertArrayEquals(textFrameEmptyPayload(), ws.passThroughFrame());
		assertNull(ws.frameAvailable());
	}

	@Test
	public void emptyPayloadBinaryFrameIsPassedThrough() throws Exception {
		WebSocket ws = new WebSocket();
		ws.frameArrived(binaryFrameEmptyPayload());
		assertArrayEquals(binaryFrameEmptyPayload(), ws.passThroughFrame());
		assertNull(ws.frameAvailable());
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
