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

import org.apache.commons.lang3.ArrayUtils;

import packetproxy.http2.Http2;
import packetproxy.http2.frames.Frame;

public class EncodeHTTP2 extends Encoder
{
	private Http2 h2client;
	private Http2 h2server;

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
		return http;
	}
	
	@Override
	public byte[] encodeClientRequest(byte[] http) throws Exception {
		byte[] a = h2client.httpToFrames(http);
		//System.out.println(new Binary(a).toHexString());
		return a;
	}

	@Override
	public byte[] decodeServerResponse(byte[] http) throws Exception {
		return http;
	}

	@Override
	public byte[] encodeServerResponse(byte[] http) throws Exception {
		byte[] hoge = h2server.httpToFrames(http);
		byte[] a = hoge.clone();
		int len;
		while ((len = Http2.parseFrameDelimiter(a)) > 0) {
			Frame f = new Frame(ArrayUtils.subarray(a, 0, len));
			System.out.println("    server: " + f);
			a = ArrayUtils.subarray(a, len, a.length);
			if (a.length == 0) {
				break;
			}
		}
		return hoge;
	}
}
