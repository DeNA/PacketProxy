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

import packetproxy.http2.Http2;

public class EncodeHTTP2 extends Encoder
{
	private Http2 http2;

	public EncodeHTTP2() throws Exception {
		http2 = new Http2();
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
	public void clientRequestArrived(byte[] frame) throws Exception { http2.writeClientFrame(frame); }
	@Override
	public void serverResponseArrived(byte[] frame) throws Exception { http2.writeServerFrame(frame); }
	@Override
	public byte[] passThroughClientRequest() throws Exception { return http2.readClientControlFrames(); }
	@Override
	public byte[] passThroughServerResponse() throws Exception { return http2.readServerControlFrames(); }
	@Override
	public byte[] clientRequestAvailable() throws Exception { return http2.readClientFrames(); }
	@Override
	public byte[] serverResponseAvailable() throws Exception { return http2.readServerFrames(); }

	@Override
	public byte[] decodeClientRequest(byte[] frames) throws Exception {
		return Http2.framesToHttp(frames);
	}
	
	@Override
	public byte[] encodeClientRequest(byte[] http) throws Exception {
		return Http2.httpToFrames(http);
	}

	@Override
	public byte[] decodeServerResponse(byte[] frames) throws Exception {
		return Http2.framesToHttp(frames);
	}

	@Override
	public byte[] encodeServerResponse(byte[] http) throws Exception {
		return Http2.httpToFrames(http);
	}
}
