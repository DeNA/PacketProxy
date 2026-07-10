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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

public class PingFrameHandlingTest {

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
	 * A PING without the ACK flag must be answered on the same connection with a PING
	 * carrying the ACK flag and identical payload, and must not be relayed as a control
	 * frame to the other connection (RFC 7540 6.7).
	 */
	@Test
	public void pingIsAnsweredLocallyWithAck() throws Exception {
		FrameManager fm = new FrameManager();
		// length=8, type=PING(0x06), flags=0x00, streamId=0, payload=1122334455667788
		fm.write(Hex.decodeHex("0000080600000000001122334455667788".toCharArray()));

		// The PING is not forwarded to the peer connection.
		assertTrue(fm.readControlFrames().isEmpty());

		// A PING+ACK with the identical payload is sent back to the origin connection.
		byte[] reply = readAvailable(fm.getFlowControlledInputStream());
		assertArrayEquals(Hex.decodeHex("0000080601000000001122334455667788".toCharArray()), reply);
	}

	/*
	 * A PING with the ACK flag is a response to a PING PacketProxy never sends, so it is
	 * dropped: neither relayed nor answered.
	 */
	@Test
	public void pingAckIsDropped() throws Exception {
		FrameManager fm = new FrameManager();
		// flags=0x01 (ACK)
		fm.write(Hex.decodeHex("0000080601000000009988776655443322".toCharArray()));

		assertTrue(fm.readControlFrames().isEmpty());
		assertEquals(0, fm.getFlowControlledInputStream().available());
	}
}
