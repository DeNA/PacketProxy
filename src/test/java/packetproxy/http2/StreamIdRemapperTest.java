/*
 * Copyright 2026 DeNA Co., Ltd.
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
package packetproxy.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class StreamIdRemapperTest {

	/* Build a minimal frame header: length=payloadLen, type, flags=0, streamId. */
	private byte[] frame(int type, int streamId, int payloadLen) {
		ByteBuffer bb = ByteBuffer.allocate(9 + payloadLen);
		bb.put((byte) ((payloadLen >>> 16) & 0xff));
		bb.put((byte) ((payloadLen >>> 8) & 0xff));
		bb.put((byte) (payloadLen & 0xff));
		bb.put((byte) type);
		bb.put((byte) 0);
		bb.putInt(streamId);
		bb.put(new byte[payloadLen]);
		return bb.array();
	}

	private int streamIdOf(byte[] f) {
		return ((f[5] & 0x7f) << 24) | ((f[6] & 0xff) << 16) | ((f[7] & 0xff) << 8) | (f[8] & 0xff);
	}

	private static final int HEADERS = 0x1;
	private static final int DATA = 0x0;

	/*
	 * The client opened streams 23 then 25, but PacketProxy sends request 25 to the server
	 * first (buffer-until-END reordering). The remapper must hand the server strictly
	 * increasing IDs in *send* order regardless of the original client IDs.
	 */
	@Test
	public void allocatesIncreasingServerIdsInSendOrder() {
		StreamIdRemapper m = new StreamIdRemapper();

		// stream 25 opened first (sent first) -> server id 1
		assertEquals(1, m.mapClientToServer(25, true));
		// its DATA follows the same mapping
		assertEquals(1, m.mapClientToServer(25, false));
		// stream 23 opened later (sent later) -> server id 3 (still increasing on the
		// wire)
		assertEquals(3, m.mapClientToServer(23, true));
		assertEquals(3, m.mapClientToServer(23, false));

		// responses come back on the server IDs and must map back to the client IDs
		assertEquals(25, m.mapServerToClient(1));
		assertEquals(23, m.mapServerToClient(3));
	}

	/* DATA before its HEADERS (no allocation) falls back to identity rather than inventing an ID. */
	@Test
	public void dataWithoutAllocationIsIdentity() {
		StreamIdRemapper m = new StreamIdRemapper();
		assertEquals(99, m.mapClientToServer(99, false));
	}

	/* Response byte stream: HEADERS/DATA stream IDs are rewritten to the client's; others untouched. */
	@Test
	public void rewriteResponseMapsHeadersAndDataBack() throws Exception {
		StreamIdRemapper m = new StreamIdRemapper();
		m.mapClientToServer(25, true); // -> 1
		m.mapClientToServer(23, true); // -> 3

		byte[] respFor25 = concat(frame(HEADERS, 1, 4), frame(DATA, 1, 8));
		byte[] rewritten = m.rewriteResponseToClient(respFor25);
		assertEquals(25, streamIdOf(rewritten)); // HEADERS
		assertEquals(25, streamIdOf(java.util.Arrays.copyOfRange(rewritten, 9 + 4, rewritten.length))); // DATA

		byte[] respFor23 = frame(HEADERS, 3, 4);
		assertEquals(23, streamIdOf(m.rewriteResponseToClient(respFor23)));
	}

	/* An unknown server stream ID (e.g. no mapping) is left as-is instead of dropped. */
	@Test
	public void rewriteResponseUnknownIdIsIdentity() throws Exception {
		StreamIdRemapper m = new StreamIdRemapper();
		byte[] resp = frame(HEADERS, 7, 4);
		assertEquals(7, streamIdOf(m.rewriteResponseToClient(resp)));
	}

	private byte[] concat(byte[] a, byte[] b) {
		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
