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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;

public class FrameManagerStreamIdTest {

	private byte[] headersFrame(int streamId) {
		byte[] payload = new byte[]{(byte) 0x82}; // arbitrary HPACK bytes, opaque here
		ByteBuffer bb = ByteBuffer.allocate(9 + payload.length);
		bb.put((byte) 0).put((byte) 0).put((byte) payload.length);
		bb.put((byte) Frame.Type.HEADERS.ordinal());
		bb.put((byte) 0x04); // END_HEADERS
		bb.putInt(streamId);
		bb.put(payload);
		return bb.array();
	}

	private byte[] readAvailable(InputStream in) throws Exception {
		int n = in.available();
		byte[] buf = new byte[n];
		int read = 0;
		while (read < n) {

			read += in.read(buf, read, n - read);
		}
		return buf;
	}

	/*
	 * Reproduces the GOAWAY(PROTOCOL_ERROR) scenario at the FrameManager wiring level:
	 * requests reach the server-facing queue out of client order (25 then 23), and the
	 * remapper must emit HEADERS to the server with strictly increasing stream IDs.
	 */
	@Test
	public void outgoingHeadersGetIncreasingServerStreamIds() throws Exception {
		FrameManager serverFm = new FrameManager();
		serverFm.setStreamIdRemapper(new StreamIdRemapper());

		serverFm.putToFlowControlledQueue(headersFrame(25));
		serverFm.putToFlowControlledQueue(headersFrame(23));

		byte[] out = readAvailable(serverFm.getFlowControlledInputStream());
		List<Frame> frames = FrameUtils.parseFrames(out);

		assertEquals(2, frames.size());
		assertEquals(Frame.Type.HEADERS, frames.get(0).getType());
		assertEquals(Frame.Type.HEADERS, frames.get(1).getType());
		// Client sent 25 first then 23; on the server wire they must be increasing.
		assertEquals(1, frames.get(0).getStreamId());
		assertEquals(3, frames.get(1).getStreamId());
	}

	/* Without a remapper (client-facing FrameManager), stream IDs pass through unchanged. */
	@Test
	public void withoutRemapperStreamIdsUnchanged() throws Exception {
		FrameManager fm = new FrameManager();

		fm.putToFlowControlledQueue(headersFrame(25));
		fm.putToFlowControlledQueue(headersFrame(23));

		byte[] out = readAvailable(fm.getFlowControlledInputStream());
		List<Frame> frames = FrameUtils.parseFrames(out);

		assertEquals(25, frames.get(0).getStreamId());
		assertEquals(23, frames.get(1).getStreamId());
	}
}
