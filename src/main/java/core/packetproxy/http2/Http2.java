/*
 * Copyright 2019 DeNA Co., Ltd.
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

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jetty.http2.hpack.HpackEncoder;
import packetproxy.common.UniqueID;
import packetproxy.http.Http;
import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;
import packetproxy.http2.frames.HeadersFrame;
import packetproxy.model.Packet;

public class Http2 extends FramesBase {
	private StreamManager clientStreamManager = new StreamManager();
	private StreamManager serverStreamManager = new StreamManager();

	public Http2() throws Exception {
		super();
	}

	@Override
	public String getName() {
		return "HTTP2";
	}

	@Override
	protected byte[] passFramesToDecodeClientRequest(List<Frame> frames) throws Exception {
		return filterFrames(clientStreamManager, frames);
	}
	@Override
	protected byte[] passFramesToDecodeServerResponse(List<Frame> frames) throws Exception {
		return filterFrames(serverStreamManager, frames);
	}

	private byte[] filterFrames(StreamManager streamManager, List<Frame> frames) throws Exception {
		for (Frame frame : frames) {
			if (frame instanceof HeadersFrame) {
				streamManager.write(frame);
			} else if (frame instanceof DataFrame) {
				streamManager.write(frame);
			}
			if ((frame.getFlags() & 0x01) > 0) {
				List<Frame> stream = streamManager.read(frame.getStreamId());
				return FrameUtils.toByteArray(stream);
			}
		}
		return null;
	}

	@Override
	protected byte[] decodeClientRequestFromFrames(byte[] frames) throws Exception {
		return decodeFromFrames(frames);
	}
	@Override
	protected byte[] decodeServerResponseFromFrames(byte[] frames) throws Exception {
		return decodeFromFrames(frames);
	}

	private byte[] decodeFromFrames(byte[] frames) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame : FrameUtils.parseFrames(frames)) {
			if (frame instanceof HeadersFrame) {
				HeadersFrame headersFrame = (HeadersFrame) frame;
				out.write(headersFrame.getHttp());
			} else if (frame instanceof DataFrame) {
				DataFrame dataFrame = (DataFrame) frame;
				out.write(dataFrame.getPayload());
			}
		}
		Http http = Http.create(out.toByteArray());
		int flags = Integer.valueOf(http.getFirstHeader("X-PacketProxy-HTTP2-Flags"));
		if (http.getBody() == null || http.getBody().length == 0) {
			http.updateHeader("X-PacketProxy-HTTP2-Flags", String.valueOf(flags & 0xff | HeadersFrame.FLAG_END_STREAM));
		}
		return http.toByteArray();
	}

	@Override
	protected byte[] encodeClientRequestToFrames(byte[] http) throws Exception {
		return encodeToFrames(http, super.getClientHpackEncoder());
	}
	@Override
	protected byte[] encodeServerResponseToFrames(byte[] http) throws Exception {
		return encodeToFrames(http, super.getServerHpackEncoder());
	}

	private byte[] encodeToFrames(byte[] data, HpackEncoder encoder) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Http http = Http.create(data);
		int flags = Integer.valueOf(http.getFirstHeader("X-PacketProxy-HTTP2-Flags"));
		if (http.getBody() != null && http.getBody().length > 0) {
			http.updateHeader("X-PacketProxy-HTTP2-Flags",
					String.valueOf(flags & 0xff & ~HeadersFrame.FLAG_END_STREAM));
			HeadersFrame headersFrame = new HeadersFrame(http);
			out.write(headersFrame.toByteArrayWithoutExtra(encoder));
			DataFrame dataFrame = new DataFrame(http);
			out.write(dataFrame.toByteArrayWithoutExtra());
		} else {
			http.updateHeader("X-PacketProxy-HTTP2-Flags", String.valueOf(flags & 0xff | HeadersFrame.FLAG_END_STREAM));
			HeadersFrame headersFrame = new HeadersFrame(http);
			out.write(headersFrame.toByteArrayWithoutExtra(encoder));
		}
		return out.toByteArray();
	}

	/* key: streamId, value: groupId */
	private Map<Long, Long> groupMap = new HashMap<>();

	public void setGroupId(Packet packet) throws Exception {
		byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
		Http http = Http.create(data);
		String streamIdStr = http.getFirstHeader("X-PacketProxy-HTTP2-Stream-Id");
		if (streamIdStr != null && streamIdStr.length() > 0) {
			long streamId = Long.parseLong(streamIdStr);
			if (groupMap.containsKey(streamId)) {
				packet.setGroup(groupMap.get(streamId));
			} else {
				long groupId = UniqueID.getInstance().createId();
				groupMap.put(streamId, groupId);
				packet.setGroup(groupId);
			}
		}
	}
}
