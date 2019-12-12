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

import packetproxy.common.WebSocket;
import packetproxy.http.Http;
import packetproxy.model.Packet;

public class EncodeHTTPWebSocket extends Encoder
{
	protected boolean binary_start = false;

	public EncodeHTTPWebSocket(String ALPN) throws Exception {
		super(ALPN);
	}

	public EncodeHTTPWebSocket()
	{
	}

	public boolean useNewConnectionForResend() {
		return false;
	}
	
	@Override
	public String getName()
	{
		return "HTTP WebSocket";
	}
	
	@Override
	public int checkDelimiter(byte[] input) throws Exception
	{
		if (binary_start) {
			return WebSocket.checkDelimiter(input);
		} else {
			return Http.parseHttpDelimiter(input);
		}
	}

	@Override
	public byte[] decodeServerResponse(Packet server_packet) throws Exception
	{
		byte[] input_data = server_packet.getReceivedData();
		if (binary_start) {
			WebSocket ws = new WebSocket(input_data);
			byte[] encodedData = ws.getPayload();
			return decodeWebsocketResponse(encodedData);
		} else {
			Http http = new Http(input_data);
			http = decodeHttpResponse(http);
			return http.toByteArray();
		}
	}

	@Override
	public byte[] encodeServerResponse(Packet server_packet) throws Exception
	{
		byte[] input_data = server_packet.getModifiedData();
		if (binary_start) {
			byte[] received_data = server_packet.getReceivedData();
			WebSocket ws_original = new WebSocket(received_data);
			byte[] encodedData = encodeWebsocketResponse(input_data);
			WebSocket ws = WebSocket.generateFromPayload(encodedData, ws_original);
			return ws.toByteArray();
		} else {
			Http http = new Http(input_data);
			// encodeでやらないと、Switching Protocolsのレスポンス自体がwebsocketとしてencodeされてしまう
			binary_start =http.getStatusCode().matches("101");
			http = encodeHttpResponse(http);
			return http.toByteArray();
		}
	}

	@Override
	public byte[] decodeClientRequest(Packet client_packet) throws Exception
	{
		byte[] input_data = client_packet.getReceivedData();
		if (binary_start) {
			WebSocket ws = new WebSocket(input_data);
			byte[] encodedData = ws.getPayload();
			return decodeWebsocketRequest(encodedData);
		} else {
			Http http = new Http(input_data);
			http = decodeHttpRequest(http);
			return http.toByteArray();
		}
	}
	
	@Override
	public byte[] encodeClientRequest(Packet client_packet) throws Exception
	{
		byte[] input_data = client_packet.getModifiedData();
		if (binary_start) {
			byte[] received_data = client_packet.getReceivedData();
			WebSocket ws_original = new WebSocket(received_data);
			byte[] encodedData = encodeWebsocketRequest(input_data);
			WebSocket ws = WebSocket.generateFromPayload(encodedData, ws_original);
			return ws.toByteArray();
		} else {
			Http http = new Http(input_data);
			http = encodeHttpRequest(http);
			return http.toByteArray();
		}
	}

	@Override
	public String getContentType(byte[] input_data) throws Exception
	{
		if (binary_start) {
			return "WebSocket";
		} else {
			Http http = new Http(input_data);
			return http.getFirstHeader("Content-Type");
		}
	}
	
	public Http decodeHttpRequest(Http input){
		return input;
	}

	public Http encodeHttpRequest(Http input){
		return input;
	}

	public Http decodeHttpResponse(Http input){
		return input;
	}

	public Http encodeHttpResponse(Http input){
		return input;
	}

	public byte[] decodeWebsocketRequest(byte[] input){
		return input;
	}

	public byte[] encodeWebsocketRequest(byte[] input){
		return input;
	}

	public byte[] decodeWebsocketResponse(byte[] input){
		return input;
	}

	public byte[] encodeWebsocketResponse(byte[] input){
		return input;
	}


	public byte[] decodeServerResponse(byte[] input_data) throws Exception { return null; }
	public byte[] encodeServerResponse(byte[] input_data) throws Exception { return null; }
	public byte[] decodeClientRequest(byte[] input_data) throws Exception { return null; }
	public byte[] encodeClientRequest(byte[] input_data) throws Exception { return null; }
}
