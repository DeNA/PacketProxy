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

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;

/**
 * Remaps HTTP/2 stream IDs between the client-facing and the server-facing
 * connection.
 *
 * <p>
 * PacketProxy terminates the two connections independently and forwards each
 * request only after it has been fully buffered (see
 * {@code Http2#filterFrames}). Concurrent requests can therefore reach the
 * server in a different order than the client opened them, which violates RFC
 * 7540 5.1.1 ("The identifier of a newly established stream MUST be numerically
 * greater than all streams that the initiating endpoint has opened") and makes
 * the server abort the connection with {@code
 * GOAWAY(PROTOCOL_ERROR)}.
 *
 * <p>
 * This class assigns a fresh, monotonically increasing server stream ID to each
 * client stream in the order the request is actually sent to the server, and
 * maps the server's response stream IDs back to the client's. Only HEADERS and
 * DATA frames carry stream IDs that need remapping here; connection-level
 * frames (stream 0) and consumed control frames (WINDOW_UPDATE / RST_STREAM,
 * kept in server-ID space for flow control) are left as-is.
 */
public class StreamIdRemapper {

	private static final int TYPE_DATA = Frame.Type.DATA.ordinal(); // 0x0
	private static final int TYPE_HEADERS = Frame.Type.HEADERS.ordinal(); // 0x1

	private final Map<Integer, Integer> clientToServer = new HashMap<>();
	private final Map<Integer, Integer> serverToClient = new HashMap<>();
	private int nextServerStreamId = 1; // client-initiated streams are odd

	/**
	 * Returns the server stream ID for a client stream ID, allocating the next
	 * increasing ID the first time a stream is opened (i.e. on its first HEADERS
	 * frame).
	 */
	public synchronized int mapClientToServer(int clientStreamId, boolean allocateIfAbsent) {
		Integer serverStreamId = clientToServer.get(clientStreamId);
		if (serverStreamId == null) {

			if (!allocateIfAbsent) {

				return clientStreamId;
			}
			serverStreamId = nextServerStreamId;
			nextServerStreamId += 2;
			clientToServer.put(clientStreamId, serverStreamId);
			serverToClient.put(serverStreamId, clientStreamId);
		}
		return serverStreamId;
	}

	/**
	 * Maps a server stream ID back to the client stream ID it was allocated for.
	 */
	public synchronized int mapServerToClient(int serverStreamId) {
		Integer clientStreamId = serverToClient.get(serverStreamId);
		return (clientStreamId != null) ? clientStreamId : serverStreamId;
	}

	/**
	 * Rewrites the stream IDs of HEADERS/DATA frames in a server->client byte
	 * stream back to the client's stream IDs. The input must consist of whole
	 * frames.
	 */
	public synchronized byte[] rewriteResponseToClient(byte[] frames) throws Exception {
		byte[] out = frames.clone();
		int pos = 0;
		while (pos < out.length) {

			byte[] remaining = ArrayUtils.subarray(out, pos, out.length);
			int delim = FrameUtils.checkDelimiter(remaining);
			if (delim <= 0) {

				break;
			}
			if (!FrameUtils.isPreface(remaining)) {

				int type = out[pos + 3] & 0xff;
				if (type == TYPE_HEADERS || type == TYPE_DATA) {

					int serverStreamId = readStreamId(out, pos);
					if (serverStreamId != 0) {

						writeStreamId(out, pos, mapServerToClient(serverStreamId));
					}
				}
			}
			pos += delim;
		}
		return out;
	}

	private static int readStreamId(byte[] data, int frameOffset) {
		return ((data[frameOffset + 5] & 0x7f) << 24) | ((data[frameOffset + 6] & 0xff) << 16)
				| ((data[frameOffset + 7] & 0xff) << 8) | (data[frameOffset + 8] & 0xff);
	}

	private static void writeStreamId(byte[] data, int frameOffset, int streamId) {
		data[frameOffset + 5] = (byte) ((data[frameOffset + 5] & 0x80) | ((streamId >>> 24) & 0x7f));
		data[frameOffset + 6] = (byte) ((streamId >>> 16) & 0xff);
		data[frameOffset + 7] = (byte) ((streamId >>> 8) & 0xff);
		data[frameOffset + 8] = (byte) (streamId & 0xff);
	}
}
