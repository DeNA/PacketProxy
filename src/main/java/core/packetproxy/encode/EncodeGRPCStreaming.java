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

import packetproxy.http.Http;
import packetproxy.http2.FramesBase;
import packetproxy.http2.GrpcStreaming;
import packetproxy.model.Packet;

public class EncodeGRPCStreaming extends Encoder
{
	private FramesBase http2 = new GrpcStreaming();
	
	@Override
	public String getName() {
		return "gRPC Streaming";
	}
	
	public EncodeGRPCStreaming() throws Exception {
		super();
	}

	public EncodeGRPCStreaming(String ALPN) throws Exception {
		super("h2");
	}

	@Override
	public int checkDelimiter(byte[] data) throws Exception {
		return http2.checkDelimiter(data);
	}

	@Override
	public void clientRequestArrived(byte[] frames) throws Exception {
		http2.clientRequestArrived(frames);
	}

	@Override
	public void serverResponseArrived(byte[] frames) throws Exception {
		http2.serverResponseArrived(frames);
	}

	@Override
	public byte[] passThroughClientRequest() throws Exception {
		return http2.passThroughClientRequest();
	}

	@Override
	public byte[] passThroughServerResponse() throws Exception {
		return http2.passThroughServerResponse();
	}

	@Override
	public byte[] clientRequestAvailable() throws Exception {
		return http2.clientRequestAvailable();
	}

	@Override
	public byte[] serverResponseAvailable() throws Exception {
		return http2.serverResponseAvailable();
	}

	@Override
	public byte[] decodeClientRequest(byte[] input) throws Exception {
		return http2.decodeClientRequest(input);
	}

	@Override
	public byte[] encodeClientRequest(byte[] input) throws Exception {
		return http2.encodeClientRequest(input);
	}

	@Override
	public byte[] decodeServerResponse(byte[] input) throws Exception {
		return http2.decodeServerResponse(input);
	}

	@Override
	public byte[] encodeServerResponse(byte[] input) throws Exception {
		return http2.encodeServerResponse(input);
	}

	@Override
	public String getSummarizedResponse(Packet packet)
	{
		String summary = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		try {
			byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
			Http http = new Http(data);
			String statusCode = http.getStatusCode();
			summary = statusCode;
		} catch (Exception e) {
			e.printStackTrace();
			summary = "Headlineを生成できません・・・";
		}
		return summary;
	}

	@Override
	public String getSummarizedRequest(Packet packet)
	{
		String summary = "";
		String statusCode = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		try {
			byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();                                                                                                                                                              
			Http http = new Http(data);
			statusCode = http.getStatusCode();
			summary = http.getMethod() + " " + http.getURL(packet.getServerPort(), packet.getUseSSL());
		} catch (Exception e) { 
			if (statusCode != null && statusCode.length() > 0) {
				summary = statusCode;
			} else {
				e.printStackTrace();
				summary = "Headlineを生成できません・・・";
			}
		}
		return summary;
	}
}
