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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http2.hpack.HpackEncoder;
import packetproxy.common.UniqueID;
import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;
import packetproxy.http2.frames.HeadersFrame;
import packetproxy.model.Packet;

public class GrpcStreaming extends FramesBase {

	private StreamManager clientStreamManager = new StreamManager();
	private StreamManager serverStreamManager = new StreamManager();
	private Map<Integer, HeadersFrame> clientStreamFirstHeaderMap = new HashMap<>();
	private Map<Integer, HeadersFrame> serverStreamFirstHeaderMap = new HashMap<>();

	public GrpcStreaming() throws Exception {
		super();
	}

	@Override
	public String getName() {
		return "gRPCStreaming";
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

			streamManager.write(frame);
		}
		Frame frame = streamManager.popOneFrame();
		if (frame == null) {

			return new byte[0];
		}
		return frame.toByteArray();
	}

	@Override
	protected byte[] decodeClientRequestFromFrames(byte[] frames) throws Exception {
		return decodeFromFrames(frames, clientStreamFirstHeaderMap);
	}

	@Override
	protected byte[] decodeServerResponseFromFrames(byte[] frames) throws Exception {
		return decodeFromFrames(frames, serverStreamFirstHeaderMap);
	}

	// frame は1つずつ処理する
	// 最初に来たヘッダフレームの値を使い回してヘッダ部分を埋める
	private byte[] decodeFromFrames(byte[] frames, Map<Integer, HeadersFrame> streamFirstHeaderMap) throws Exception {
		ByteArrayOutputStream outHeader = new ByteArrayOutputStream();
		ByteArrayOutputStream outData = new ByteArrayOutputStream();
		Http httpHeaderSums = null;

		List<Frame> parsedFrames = FrameUtils.parseFrames(frames);
		if (parsedFrames.size() != 1) {

			throw new Exception("処理対象のフレームが複数あります");
		}
		for (Frame frame : parsedFrames) {

			HeadersFrame firstHeaderFrame = streamFirstHeaderMap.get(frame.getStreamId());
			boolean isFirstHeaderFrame = firstHeaderFrame == null;
			if (isFirstHeaderFrame) {

				// Header Frame
				if (!(frame instanceof HeadersFrame)) {

					throw new Exception("ヘッダフレームの前にデータフレームがあります");
				}
				firstHeaderFrame = (HeadersFrame) frame;
				streamFirstHeaderMap.put(frame.getStreamId(), (HeadersFrame) frame);
				httpHeaderSums = Http.create(firstHeaderFrame.getHttp());
			} else if (frame instanceof HeadersFrame) {

				// Trailer Header Frame
				httpHeaderSums = Http.create(firstHeaderFrame.getHttp());
				HeadersFrame headersFrame = (HeadersFrame) frame;
				Http http = Http.create(headersFrame.getHttp());
				for (HeaderField field : http.getHeader().getFields()) {

					httpHeaderSums.updateHeader("x-trailer-" + field.getName(), field.getValue());
				}
				httpHeaderSums.updateHeader("X-PacketProxy-HTTP2-TrailerHeaderFrame", "true");
				httpHeaderSums.setPath("/trailer-header-frame");
			} else {

				// Data Frame
				httpHeaderSums = Http.create(firstHeaderFrame.getHttp());
				DataFrame dataFrame = (DataFrame) frame;
				outData.write(dataFrame.getPayload());
				httpHeaderSums.setPath("/data-frame");
				httpHeaderSums.updateHeader("X-PacketProxy-HTTP2-Type", "0");
			}
			int flags = frame.getFlags();
			httpHeaderSums.updateHeader("X-PacketProxy-HTTP2-Flags", String.valueOf(flags & 0xff));
			if (!isFirstHeaderFrame) {

				// 余分なヘッダを削除
				List<String> unusedHeaders = new ArrayList<>();
				for (HeaderField field : httpHeaderSums.getHeader().getFields()) {

					if (field.getName().startsWith("X-PacketProxy") || field.getName().startsWith("x-trailer-")) {

						continue;
					}
					unusedHeaders.add(field.getName());
				}
				for (String name : unusedHeaders) {

					httpHeaderSums.removeHeader(name);
				}
			}
		}
		outHeader.write(httpHeaderSums.toByteArray());
		outData.writeTo(outHeader);
		Http http = Http.create(outHeader.toByteArray());
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
		HttpFields.Mutable GRPC2ndHeaderHttpFields = HttpFields.build();

		List<String> unusedHeaders = new ArrayList<>();
		boolean isTrailerHeaderFrame = false;
		boolean isDataFrame = false;
		for (HeaderField field : http.getHeader().getFields()) {

			// Logging.log("header: " + field.getName());
			if (field.getName().startsWith("x-trailer-")) {

				unusedHeaders.add(field.getName());
				GRPC2ndHeaderHttpFields.add(field.getName().substring(10), field.getValue());
			} else if (field.getName().equals("X-PacketProxy-HTTP2-TrailerHeaderFrame")) {

				isTrailerHeaderFrame = true;
			} else if (field.getName().equals("X-PacketProxy-HTTP2-Type") && Integer.valueOf(field.getValue()) == 0) {

				isDataFrame = true;
			}
		}
		for (String name : unusedHeaders) {

			http.removeHeader(name);
		}
		// Logging.log("isDataFrame: " + isDataFrame);

		if (!isTrailerHeaderFrame && !isDataFrame) {

			// First Header Frame
			HeadersFrame headersFrame = new HeadersFrame(http);
			out.write(headersFrame.toByteArrayWithoutExtra(encoder, false, false));
		} else if (isTrailerHeaderFrame) {

			// Trailer Header Frame
			Http althttp = http;
			althttp.setBody(new byte[0]);
			althttp.removeMatches("^(?!X-PacketProxy-HTTP2).*$");

			for (HttpField headerField : GRPC2ndHeaderHttpFields) {

				althttp.updateHeader(headerField.getName(), headerField.getValue());
			}
			althttp.updateHeader("X-PacketProxy-HTTP2-Flags", String.valueOf(flags));
			HeadersFrame headers2ndFrame = new HeadersFrame(althttp);
			out.write(headers2ndFrame.toByteArrayWithoutExtra(encoder));
		} else {

			// Data Frame
			DataFrame dataFrame = new DataFrame(http);
			dataFrame.setFlags(flags);
			out.write(dataFrame.toByteArrayWithoutExtra());
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
