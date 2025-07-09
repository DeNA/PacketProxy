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
import packetproxy.http2.FramesBase;
import packetproxy.http2.Http2;
import packetproxy.http3.service.Http3;
import packetproxy.model.Packet;

public abstract class EncodeHTTPBase extends Encoder {
	public enum HTTPVersion {
		HTTP1, HTTP2, HTTP3
	}
	private HTTPVersion httpVersion;
	private FramesBase http2;
	private Http3 http3;
	private String requestMethod = "";

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
		} else if (ALPN.equals("h2") || ALPN.equals("grpc") || ALPN.equals("grpc-exp")) {
			httpVersion = HTTPVersion.HTTP2;
			http2 = new Http2();
		} else if (ALPN.equals("h3")) {
			httpVersion = HTTPVersion.HTTP3;
			http3 = new Http3();
		} else {
			httpVersion = HTTPVersion.HTTP1;
		}
	}

	public EncodeHTTPBase(String ALPN, FramesBase http2CustomFrame) throws Exception {
		super(ALPN);
		httpVersion = HTTPVersion.HTTP2;
		this.http2 = http2CustomFrame;
	}

	public HTTPVersion getHttpVersion() {
		return httpVersion;
	}

	@Override
	public int checkDelimiter(byte[] data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return Http.parseHttpDelimiter(data);
		} else if (this.httpVersion == HTTPVersion.HTTP2) {
			return http2.checkDelimiter(data);
		} else {
			return http3.checkDelimiter(data);
		}
	}

	@Override
	public int checkResponseDelimiter(byte[] data) throws Exception {
		if (this.requestMethod.equals("HEAD")) {
			return data.length;
		}
		return checkDelimiter(data);
	}

	@Override
	public void clientRequestArrived(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			super.clientRequestArrived(frames);
		} else if (this.httpVersion == HTTPVersion.HTTP2) {
			http2.clientRequestArrived(frames);
		} else {
			http3.clientRequestArrived(frames);
		}
	}

	@Override
	public void serverResponseArrived(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			super.serverResponseArrived(frames);
		} else if (this.httpVersion == HTTPVersion.HTTP2) {
			http2.serverResponseArrived(frames);
		} else {
			http3.serverResponseArrived(frames);
		}
	}

	@Override
	public byte[] passThroughClientRequest() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.passThroughClientRequest();
		} else if (this.httpVersion == HTTPVersion.HTTP2) {
			return http2.passThroughClientRequest();
		} else {
			return http3.passThroughClientRequest();
		}
	}

	@Override
	public byte[] passThroughServerResponse() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.passThroughServerResponse();
		} else if (this.httpVersion == HTTPVersion.HTTP2) {
			return http2.passThroughServerResponse();
		} else {
			return http3.passThroughServerResponse();
		}
	}

	@Override
	public byte[] clientRequestAvailable() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.clientRequestAvailable();
		} else if (this.httpVersion == HTTPVersion.HTTP2) {
			return http2.clientRequestAvailable();
		} else {
			return http3.clientRequestAvailable();
		}
	}

	@Override
	public byte[] serverResponseAvailable() throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP1) {
			return super.serverResponseAvailable();
		} else if (this.httpVersion == HTTPVersion.HTTP2) {
			return http2.serverResponseAvailable();
		} else {
			return http3.serverResponseAvailable();
		}
	}

	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP2) {
			input_data = http2.decodeClientRequest(input_data);
		} else if (this.httpVersion == HTTPVersion.HTTP3) {
			input_data = http3.decodeClientRequest(input_data);
		}
		Http http = Http.create(input_data);
		Http decodedHttp = http;
		if (http.getFirstHeader("X-PacketProxy-Skip-ClientSideEncode").contains("true")) {
			encode_mode = 1;
		} else {
			decodedHttp = decodeClientRequestHttp(http);
		}
		return decodedHttp.toByteArray();
	}

	@Override
	public byte[] encodeClientRequest(byte[] input_data) throws Exception {
		Http http = Http.create(input_data);
		this.requestMethod = http.getMethod();
		Http encodedHttp = encodeClientRequestHttp(http);
		byte[] encodedData = encodedHttp.toByteArray();
		if (this.httpVersion == HTTPVersion.HTTP2) {
			encodedData = http2.encodeClientRequest(encodedData);
		} else if (this.httpVersion == HTTPVersion.HTTP3) {
			encodedData = http3.encodeClientRequest(encodedData);
		}
		return encodedData;
	}

	@Override
	public final byte[] decodeServerResponse(byte[] input_data) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP2) {
			input_data = http2.decodeServerResponse(input_data);
		} else if (this.httpVersion == HTTPVersion.HTTP3) {
			input_data = http3.decodeServerResponse(input_data);
		}
		Http http;
		if (this.requestMethod.equals("HEAD")) {
			http = Http.createWithoutTouchingContentLength(input_data);
		} else {
			http = Http.create(input_data);
		}
		Http decodedHttp = decodeServerResponseHttp(http);
		return decodedHttp.toByteArray();
	}

	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		Http http;
		if (this.requestMethod.equals("HEAD")) {
			http = Http.createWithoutTouchingContentLength(input_data);
		} else {
			http = Http.create(input_data);
		}
		Http encodedHttp = http;
		if (encode_mode == 0) {
			encodedHttp = encodeServerResponseHttp(http);
		}
		byte[] encodedData = encodedHttp.toByteArray();
		if (this.httpVersion == HTTPVersion.HTTP2) {
			encodedData = http2.encodeServerResponse(encodedData);
		} else if (this.httpVersion == HTTPVersion.HTTP3) {
			encodedData = http3.encodeServerResponse(encodedData);
		}
		return encodedData;
	}

	@Override
	public void putToClientFlowControlledQueue(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP2) {
			http2.putToClientFlowControlledQueue(frames);
		} else {
			super.putToClientFlowControlledQueue(frames);
		}
	}

	@Override
	public void putToServerFlowControlledQueue(byte[] frames) throws Exception {
		if (this.httpVersion == HTTPVersion.HTTP2) {
			http2.putToServerFlowControlledQueue(frames);
		} else {
			super.putToServerFlowControlledQueue(frames);
		}
	}

	@Override
	public InputStream getClientFlowControlledInputStream() {
		if (this.httpVersion == HTTPVersion.HTTP2) {
			return http2.getClientFlowControlledInputStream();
		} else {
			return super.getClientFlowControlledInputStream();
		}
	}

	@Override
	public InputStream getServerFlowControlledInputStream() {
		if (this.httpVersion == HTTPVersion.HTTP2) {
			return http2.getServerFlowControlledInputStream();
		} else {
			return super.getServerFlowControlledInputStream();
		}
	}

	@Override
	public String getContentType(byte[] input_data) throws Exception {
		Http http = Http.create(input_data);
		return http.getFirstHeader("Content-Type");
	}

	@Override
	public String getSummarizedResponse(Packet packet) {
		String summary = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) {
			return "";
		}
		try {
			byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
			Http http = Http.create(data);
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
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) {
			return "";
		}
		try {
			byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
			Http http = Http.create(data);
			summary = http.getMethod() + " " + http.getURL(packet.getServerPort(), packet.getUseSSL());
		} catch (Exception e) {
			e.printStackTrace();
			summary = "Headlineを生成できません・・・";
		}
		return summary;
	}

	@Override
	public void setGroupId(Packet packet) throws Exception {
		if (httpVersion == HTTPVersion.HTTP2) {
			http2.setGroupId(packet);
		} else if (httpVersion == HTTPVersion.HTTP3) {
			http3.setGroupId(packet);
		} else {
			super.setGroupId(packet);
		}
	}

	protected abstract Http decodeServerResponseHttp(Http inputHttp) throws Exception;
	protected abstract Http encodeServerResponseHttp(Http inputHttp) throws Exception;
	protected abstract Http decodeClientRequestHttp(Http inputHttp) throws Exception;
	protected abstract Http encodeClientRequestHttp(Http inputHttp) throws Exception;

}
