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
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.http2.hpack.HpackEncoder;

import packetproxy.common.Protobuf3;
import packetproxy.common.StringUtils;
import packetproxy.http.Http;
import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;
import packetproxy.http2.frames.HeadersFrame;
import packetproxy.model.Packet;

public class GrpcStreaming extends FramesBase
{
	public GrpcStreaming() throws Exception {
		super();
	}

	@Override
	public String getName() {
		return "HTTP2 Frames";
	}

	@Override
	protected byte[] passFramesToDecodeClientRequest(List<Frame> frames) throws Exception { return filterFrames(clientFrames, frames); }
	@Override
	protected byte[] passFramesToDecodeServerResponse(List<Frame> frames) throws Exception { return filterFrames(serverFrames, frames); }
	
	private List<Frame> clientFrames = new LinkedList<>();
	private List<Frame> serverFrames = new LinkedList<>();

	private byte[] filterFrames(List<Frame> masterFrames, List<Frame> frames) throws Exception {
		for (Frame frame : frames) {
			masterFrames.add(frame);
		}
		if (masterFrames.size() > 0) {
			Frame frame = masterFrames.remove(0);
			return frame.toByteArray();
		}
		return null;
	}

	@Override
	protected byte[] decodeClientRequestFromFrames(byte[] frames) throws Exception { return decodeFromFrames(frames); }
	@Override
	protected byte[] decodeServerResponseFromFrames(byte[] frames) throws Exception { return decodeFromFrames(frames); }

	private byte[] decodeFromFrames(byte[] frames) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (Frame frame : FrameUtils.parseFrames(frames)) {
			if (frame instanceof HeadersFrame) {
				HeadersFrame headersFrame = (HeadersFrame)frame;
				Http http = Http.create(headersFrame.getHttp());
				if(!http.getFirstHeader("grpc-status").equals("")){
					// Trailer Header Frame doesn't have headers below.(ref: HeadersFrame.java)
					http.updateHeader("X-PacketProxy-HTTP2-UUID", StringUtils.randomUUID());
					http.updateHeader("X-PacketProxy-HTTP2-Type", String.valueOf(Frame.Type.HEADERS.ordinal()));
					http.updateHeader("X-PacketProxy-HTTP2-Flags", String.valueOf(headersFrame.getFlags()));
					http.updateHeader("X-PacketProxy-HTTP2-Stream-Id", String.valueOf(headersFrame.getStreamId()));
					// reconstruct HeaderFrame
					headersFrame = new HeadersFrame(http);
				}
				baos.write(headersFrame.getHttp());

			} else if (frame instanceof DataFrame) {
				DataFrame dataFrame = (DataFrame)frame;
				Http http = Http.create(dataFrame.getHttp());
				byte[] payload = http.getBody();
				if(payload.length!=0) {
					byte[] data = ArrayUtils.subarray(payload, 5, payload.length);
					http.setBody(Protobuf3.decode(data).getBytes());
				}else{
					http.setBody(null);
				}
				baos.write(http.toByteArray());
			}
		}
		return baos.toByteArray();
	}

	@Override
	protected byte[] encodeClientRequestToFrames(byte[] http) throws Exception { return encodeToFrames(http, super.getClientHpackEncoder()); }
	@Override
	protected byte[] encodeServerResponseToFrames(byte[] http) throws Exception { return encodeToFrames(http, super.getServerHpackEncoder()); }
	
	private byte[] encodeToFrames(byte[] input, HpackEncoder encoder) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		Http http = Http.create(input);
		int type = Integer.parseInt(http.getFirstHeader("X-PacketProxy-HTTP2-Type"));
		if (Frame.Type.values()[type] == Frame.Type.HEADERS) {
			HeadersFrame frame = new HeadersFrame(http);
			out.write(frame.toByteArrayWithoutExtra(encoder));

		} else if (Frame.Type.values()[type] == Frame.Type.DATA) {
			if(http.getBody().length!=0) {
				byte[] encodeBytes = Protobuf3.encode(new String(http.getBody(), "UTF-8"));
				byte[] raw = new byte[5 + encodeBytes.length];

				for (int i = 0; i < encodeBytes.length; ++i) {
					raw[5 + i] = encodeBytes[i];
				}

				byte[] msgLength = ByteBuffer.allocate(4).putInt(encodeBytes.length).array();
				raw[1] = msgLength[0];
				raw[2] = msgLength[1];
				raw[3] = msgLength[2];
				raw[4] = msgLength[3];
				http.setBody(raw);
			}
			DataFrame data = new DataFrame(http);
			out.write(data.toByteArray());
		}
		return out.toByteArray();
	}

	@Override
	public void setGroupId(Packet packet) throws Exception {
	}
}
