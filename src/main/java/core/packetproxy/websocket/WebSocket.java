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
package packetproxy.websocket;

import java.nio.ByteBuffer;
import java.util.LinkedList;

public class WebSocket {

	LinkedList<WebSocketFrame> frames = new LinkedList<>();

	/**
	 * Last opcode from {@link #frameAvailable()}; used when re-encoding the payload
	 * to a wire frame.
	 */
	private OpCode lastDequeuedOpCode = OpCode.Binary;

	/**
	 * Last FIN bit from {@link #frameAvailable()}; continuation frames must be
	 * re-emitted with FIN cleared.
	 */
	private boolean lastDequeuedFin = true;

	public static int checkDelimiter(byte[] data) {
		return WebSocketFrame.checkSingleFrameDelimiter(data);
	}

	public void frameArrived(byte[] data) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(data);
		WebSocketFrame frame = WebSocketFrame.parseSingleFrame(buffer);
		frames.add(frame);
		if (buffer.remaining() > 0) {

			throw new Exception("WebSocket: packet data is remaining.");
		}
	}

	private static final byte[] NO_PASS_THROUGH = new byte[0];

	/**
	 * Control frames are no longer auto-forwarded here; each frame is surfaced
	 * through {@link #frameAvailable()} so History/Intercept stay frame-accurate.
	 * Returns an empty array (not {@code
	 * null}) so callers/tests can assert pass-through size without special-casing
	 * {@code null}.
	 */
	public byte[] passThroughFrame() throws Exception {
		return NO_PASS_THROUGH;
	}

	public byte[] frameAvailable() throws Exception {
		WebSocketFrame frame = this.frames.pollFirst();
		if (frame == null) {

			return null;
		}
		this.lastDequeuedOpCode = frame.getOpcode();
		this.lastDequeuedFin = frame.isFin();
		byte[] payload = frame.getPayload();
		return payload;
	}

	/**
	 * Opcode of the frame most recently returned from {@link #frameAvailable()}.
	 * Encode paths use this to preserve Text vs Binary when rebuilding WebSocket
	 * frames.
	 */
	public OpCode lastDequeuedOpCode() {
		return lastDequeuedOpCode;
	}

	/**
	 * FIN bit of the frame most recently returned from {@link #frameAvailable()}.
	 */
	public boolean lastDequeuedFin() {
		return lastDequeuedFin;
	}
}
