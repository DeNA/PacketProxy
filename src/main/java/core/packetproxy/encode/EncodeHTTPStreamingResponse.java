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

import java.io.InputStream;

import packetproxy.http.Http;
import packetproxy.http1.Http1StreamingResponse;
import packetproxy.http2.Http2StreamingResponse;
import packetproxy.model.Packet;

public class EncodeHTTPStreamingResponse extends Encoder
{
	public enum HTTPVersion {
		HTTP1, HTTP2	
	}
	private HTTPVersion httpVersion;
	private Http1StreamingResponse http1StreamingResponse;
	private Http2StreamingResponse http2StreamingResponse;

	public EncodeHTTPStreamingResponse() {
		super("http/1.1");
		httpVersion = HTTPVersion.HTTP1;
	}

	public EncodeHTTPStreamingResponse(String ALPN) throws Exception {
		super(ALPN);
		if (ALPN == null) {
			httpVersion = HTTPVersion.HTTP1;
		} else if (ALPN.equals("http/1.0") || ALPN.equals("http/1.1")) {
			httpVersion = HTTPVersion.HTTP1;
		} else if (ALPN.equals("h2") || ALPN.startsWith("grpc")) {                                                                                                                                 
			httpVersion = HTTPVersion.HTTP2;
		} else {
			httpVersion = HTTPVersion.HTTP1;
		}
		http1StreamingResponse = new Http1StreamingResponse();
		http2StreamingResponse = new Http2StreamingResponse();
	}
	
	public HTTPVersion getHttpVersion() { return httpVersion; }
	
	@Override
	public String getName() {
		return "HTTP Streaming Response";
	}
	
	@Override
	public int checkRequestDelimiter(byte[] data) throws Exception { 
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return http1StreamingResponse.checkRequestDelimiter(data);
		} else {
			return http2StreamingResponse.checkDelimiter(data);
		}
	}

	@Override
	public int checkResponseDelimiter(byte[] data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return http1StreamingResponse.checkResponseDelimiter(data);
		} else {
			return http2StreamingResponse.checkDelimiter(data);
		}
	}

	@Override
	public void clientRequestArrived(byte[] data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			http1StreamingResponse.clientRequestArrived(data);
		} else {
			http2StreamingResponse.clientRequestArrived(data);
		}
	}

	@Override
	public void serverResponseArrived(byte[] data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			http1StreamingResponse.serverResponseArrived(data);
		} else {
			http2StreamingResponse.serverResponseArrived(data);
		}
	}

	@Override
	public byte[] passThroughClientRequest() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return http1StreamingResponse.passThroughClientRequest();
		} else {
			return http2StreamingResponse.passThroughClientRequest();
		}
	}
	
	@Override
	public byte[] passThroughServerResponse() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return http1StreamingResponse.passThroughServerResponse();
		} else {
			return http2StreamingResponse.passThroughServerResponse();
		}
	}

	@Override
	public byte[] clientRequestAvailable() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return http1StreamingResponse.clientRequestAvailable();
		} else {
			return http2StreamingResponse.clientRequestAvailable();
		}
	}

	@Override
	public byte[] serverResponseAvailable() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return http1StreamingResponse.serverResponseAvailable();
		} else {
			return http2StreamingResponse.serverResponseAvailable();
		}
	}

	@Override
	final public byte[] decodeServerResponse(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return http1StreamingResponse.decodeServerResponse(input_data);
		} else {
			return http2StreamingResponse.decodeServerResponse(input_data);
		}
	}

	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return http1StreamingResponse.encodeServerResponse(input_data);
		} else {
			return http2StreamingResponse.encodeServerResponse(input_data);
		}
	}

	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return http1StreamingResponse.decodeClientRequest(input_data);
		} else {
			return http2StreamingResponse.decodeClientRequest(input_data);
		}
	}

	@Override
	public byte[] encodeClientRequest(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return http1StreamingResponse.encodeClientRequest(input_data);
		} else {
			return http2StreamingResponse.encodeClientRequest(input_data);
		}
	}

	@Override
	public void putToClientFlowControlledQueue(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			super.putToClientFlowControlledQueue(frames);
		} else {
			http2StreamingResponse.putToClientFlowControlledQueue(frames);
		}
	}

	@Override
	public void putToServerFlowControlledQueue(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			super.putToServerFlowControlledQueue(frames);
		} else {
			http2StreamingResponse.putToServerFlowControlledQueue(frames);
		}
	}

	@Override
	public InputStream getClientFlowControlledInputStream() {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return super.getClientFlowControlledInputStream();
		} else {
			return http2StreamingResponse.getClientFlowControlledInputStream();
		}
	}

	@Override
	public InputStream getServerFlowControlledInputStream() {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return super.getServerFlowControlledInputStream();
		} else {
			return http2StreamingResponse.getServerFlowControlledInputStream();
		}
	}

	@Override
	public String getContentType(byte[] input_data) throws Exception {
		Http http = new Http(input_data);
		return http.getFirstHeader("Content-Type");
	}

	@Override
	public String getSummarizedResponse(Packet packet) {
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
	public String getSummarizedRequest(Packet packet) {
		String summary = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		try {
			byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();                                                                                                                                                              
			Http http = new Http(data);
			summary = http.getMethod() + " " + http.getURL(packet.getServerPort());
		} catch (Exception e) { 
			e.printStackTrace();
			summary = "Headlineを生成できません・・・";
		}
		return summary;
	}

	@Override
	public void setGroupId(Packet packet) throws Exception {
		if (httpVersion == HTTPVersion.HTTP1) {
			super.setGroupId(packet);
		} else {
			http2StreamingResponse.setGroupId(packet);
		}
	}

	@Override
	public int checkDelimiter(byte[] input_data) throws Exception {
		return input_data.length;
	}

}
