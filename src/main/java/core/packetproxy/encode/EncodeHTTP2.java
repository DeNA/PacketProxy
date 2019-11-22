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
package packetproxy.encode;

import java.util.HashMap;
import java.util.Map;

import packetproxy.common.UniqueID;
import packetproxy.http.Http;
import packetproxy.http2.Http2;
import packetproxy.http2.frames.Frame;
import packetproxy.model.Packet;

public class EncodeHTTP2 extends Encoder
{
	private Http2 h2client;
	private Http2 h2server;
	private long clientStreamId;
	private long serverStreamId;

	public EncodeHTTP2() throws Exception {
		h2client = new Http2();
		h2server = new Http2(true);
	}
	
	@Override
	public String getName() {
		return "HTTP2";
	}

	@Override
	public int checkDelimiter(byte[] input) throws Exception {
		return Http2.parseFrameDelimiter(input);
	}

	@Override
	public void clientRequestArrived(byte[] frame) throws Exception {
		if (frame[0] != 'P' || frame[1] != 'R') {
			Frame f = new Frame(frame);
			System.out.println("Client:" + f);
		}
		h2client.writeFrame(frame);
	}
	@Override
	public void serverResponseArrived(byte[] frame) throws Exception {
		Frame f = new Frame(frame);
		System.out.println("Server:" + f);
		h2server.writeFrame(frame);
	}
	@Override
	public byte[] passThroughClientRequest() throws Exception { return h2client.readControlFrames(); }
	@Override
	public byte[] passThroughServerResponse() throws Exception { return h2server.readControlFrames(); }
	@Override
	public byte[] clientRequestAvailable() throws Exception { return h2client.readHttp(); }
	@Override
	public byte[] serverResponseAvailable() throws Exception { return h2server.readHttp(); }

	@Override
	public byte[] decodeClientRequest(byte[] http) throws Exception {
		return new Http(http).toByteArray();
	}
	
	@Override
	public byte[] encodeClientRequest(byte[] http) throws Exception {
		byte[] frames = h2client.httpToFrames(http);
		//byte[] a = frames.clone();
		//int len;
		//while ((len = Http2.parseFrameDelimiter(a)) > 0) {
		//	Frame f = new Frame(ArrayUtils.subarray(a, 0, len));
		//	System.out.println("--> client: " + f);
		//	a = ArrayUtils.subarray(a, len, a.length);
		//	if (a.length == 0) {
		//		break;
		//	}
		//}
		return frames;
	}

	@Override
	public byte[] decodeServerResponse(byte[] http) throws Exception {
		return new Http(http).toByteArray();
	}

	@Override
	public byte[] encodeServerResponse(byte[] http) throws Exception {
		byte[] frames = h2server.httpToFrames(http);
		//byte[] a = frames.clone();
		//int len;
		//while ((len = Http2.parseFrameDelimiter(a)) > 0) {
		//	Frame f = new Frame(ArrayUtils.subarray(a, 0, len));
		//	System.out.println("--> server: " + f);
		//	a = ArrayUtils.subarray(a, len, a.length);
		//	if (a.length == 0) {
		//		break;
		//	}
		//}
		return frames;
	}
	
	@Override
	public String getSummarizedRequest(Packet packet)
	{
		String summary = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		try {
			byte[] data = (packet.getDecodedData().length > 0) ?
			packet.getDecodedData() : packet.getModifiedData();
			Http http = new Http(data);
			String uriString = http.getFirstHeader("X-PacketProxy-HTTP2-URI");
			summary = http.getMethod() + " " + uriString;
		} catch (Exception e) {
			e.printStackTrace();
			summary = "Headlineを生成できません・・・";
		}
		return summary;
	}
	
	/* key: streamId, value: groupId */
	private Map<Long,Long> groupMap = new HashMap<>();
	@Override
	public void setGroupId(Packet packet) throws Exception {
		byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
		Http http = new Http(data);
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

	@Override
	public String getSummarizedResponse(Packet packet)
	{
		String summary = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		try {
			byte[] data = (packet.getDecodedData().length > 0) ?
			packet.getDecodedData() : packet.getModifiedData();
			Http http = new Http(data);
			String statusCode = http.getStatusCode();
			summary = statusCode;
		} catch (Exception e) {
			e.printStackTrace();
			summary = "Headlineを生成できません・・・";
		}
		return summary;
	}
}
