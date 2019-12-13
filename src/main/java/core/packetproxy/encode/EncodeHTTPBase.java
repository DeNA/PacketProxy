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
import packetproxy.http2.HTTP2;
import packetproxy.model.Packet;

public abstract class EncodeHTTPBase extends Encoder
{
	public enum HTTPVersion {
		HTTP1, HTTP2	
	}
	private HTTPVersion httpVersion;
	private HTTP2 http2;

	public EncodeHTTPBase() {
		super("http/1.1");
		httpVersion = HTTPVersion.HTTP1;
	}

	public EncodeHTTPBase(String ALPN) throws Exception {
		super(ALPN);
		if (ALPN == null) {
			httpVersion = HTTPVersion.HTTP1;
		} else if (ALPN.equals("http/1.0") || ALPN.equals("http/1.1")) {
			httpVersion = HTTPVersion.HTTP1;
		} else if (ALPN.equals("h2") || ALPN.equals("grpc")) {                                                                                                                                 
			httpVersion = HTTPVersion.HTTP2;
			http2 = new HTTP2();
		} else {
			httpVersion = HTTPVersion.HTTP1;
		}
	}
	
	public HTTPVersion getHttpVersion() { return httpVersion; }

	@Override
	public int checkDelimiter(byte[] data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return Http.parseHttpDelimiter(data);
		} else {
			return http2.checkDelimiter(data);
		}
	}

	@Override
	public void clientRequestArrived(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			super.clientRequestArrived(frames);
		} else {
			http2.clientRequestArrived(frames);
		}
	}

	@Override
	public void serverResponseArrived(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			super.serverResponseArrived(frames);
		} else {
			http2.serverResponseArrived(frames);
		}
	}

	@Override
	public byte[] passThroughClientRequest() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.passThroughClientRequest();
		} else {
			return http2.passThroughClientRequest();
		}
	}

	@Override
	public byte[] passThroughServerResponse() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.passThroughServerResponse();
		} else {
			return http2.passThroughServerResponse();
		}
	}

	@Override
	public byte[] clientRequestAvailable() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.clientRequestAvailable();
		} else {
			return http2.clientRequestAvailable();
		}
	}

	@Override
	public byte[] serverResponseAvailable() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.serverResponseAvailable();
		} else {
			return http2.serverResponseAvailable();
		}
	}

	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP2) { 
			input_data = http2.decodeClientRequest(input_data);
		}
		Http http = new Http(input_data);
		Http decodedHttp = decodeClientRequestHttp(http);
		return decodedHttp.toByteArray();
	}

	@Override
	public byte[] encodeClientRequest(byte[] input_data) throws Exception {
		Http http = new Http(input_data);
		Http encodedHttp = encodeClientRequestHttp(http);
		byte[] encodedData = encodedHttp.toByteArray();
		if (this.httpVersion == HTTPVersion.HTTP2) { 
			encodedData = http2.encodeClientRequest(encodedData);
		}
		return encodedData;
	}

	@Override
	final public byte[] decodeServerResponse(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP2) { 
			input_data = http2.decodeServerResponse(input_data);
		}
		Http http = new Http(input_data);
		Http decodedHttp = decodeServerResponseHttp(http);
		decodedHttp.isGzipEncoded();
		return decodedHttp.toByteArray();
	}

	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		Http http = new Http(input_data);
		Http encodedHttp = encodeServerResponseHttp(http);
		byte[] encodedData = encodedHttp.toByteArray();
		if (this.httpVersion == HTTPVersion.HTTP2) { 
			encodedData = http2.encodeServerResponse(encodedData);
		}
		return encodedData;
	}

	@Override
	public void putToClientFlowControlledQueue(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			super.putToClientFlowControlledQueue(frames);
		} else {
			http2.putToClientFlowControlledQueue(frames);
		}
	}

	@Override
	public void putToServerFlowControlledQueue(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			super.putToServerFlowControlledQueue(frames);
		} else {
			http2.putToServerFlowControlledQueue(frames);
		}
	}

	@Override
	public InputStream getClientFlowControlledInputStream() {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return super.getClientFlowControlledInputStream();
		} else {
			return http2.getClientFlowControlledInputStream();
		}
	}

	@Override
	public InputStream getServerFlowControlledInputStream() {
		if (this.httpVersion == HTTPVersion.HTTP1) { 
			return super.getServerFlowControlledInputStream();
		} else {
			return http2.getServerFlowControlledInputStream();
		}
	}

	@Override
	public String getContentType(byte[] input_data) throws Exception {
		Http http = new Http(input_data);
		return http.getFirstHeader("Content-Type");
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
			http2.setGroupId(packet);
		}
	}

	abstract protected Http decodeServerResponseHttp(Http inputHttp) throws Exception;
	abstract protected Http encodeServerResponseHttp(Http inputHttp) throws Exception;
	abstract protected Http decodeClientRequestHttp(Http inputHttp) throws Exception;
	abstract protected Http encodeClientRequestHttp(Http inputHttp) throws Exception;

}
