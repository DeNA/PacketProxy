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

import com.mobius.software.mqtt.parser.MQJsonParser;
import com.mobius.software.mqtt.parser.MQParser;
import com.mobius.software.mqtt.parser.header.api.MQMessage;
import com.mobius.software.mqtt.parser.header.impl.*;
import io.netty.buffer.Unpooled;
import packetproxy.common.WebSocket;
import packetproxy.http.Http;
import packetproxy.model.Packet;

public class EncodeMQTTWebSocket extends Encoder
{
	protected boolean binary_start = false;

	public EncodeMQTTWebSocket(String ALPN) throws Exception {
		super(ALPN);
	}

	public EncodeMQTTWebSocket()
	{
	}

	public boolean useNewConnectionForResend() {
		return false;
	}
	
	@Override
	public String getName()
	{
		return "MQTTv3.1 over WebSocket";
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
			byte[] decodeData = decodeWebsocketResponse(encodedData);
			return decodeMQTT(decodeData);
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
			byte[] encodedData = encodeWebsocketResponse(encodeMQTT(input_data));
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
			byte[] decodeData = decodeWebsocketRequest(encodedData);
			return decodeMQTT(decodeData);
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
			byte[] encodedData = encodeWebsocketRequest(encodeMQTT(input_data));
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

	public static MQJsonParser parser = new MQJsonParser();
	private byte[] encodeMQTT(byte[] b) throws Exception {
        String json = new String(b);
		MQMessage m = parser.messageObject(json);
		return MQParser.encode(m).array();
	}
	private byte[] decodeMQTT(byte[] b) throws Exception {
		MQMessage m = MQParser.decode(Unpooled.copiedBuffer(b));
		return parser.jsonString(m).getBytes();
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

	@Override
	public String getSummarizedRequest(Packet packet) {
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		byte[] data = (packet.getDecodedData().length > 0) ?
				packet.getDecodedData() : packet.getModifiedData();

		try {
			if (data.length == 0) throw new Exception();
			Http http = new Http(data);
			String method = http.getMethod();
			String url = http.getURL(packet.getServerPort(), packet.getUseSSL());
			if (method == null)
				return getSummarizedMessage(encodeMQTT(data));
			return method + " " + url;
		} catch (Exception e) {
			return "Headlineを生成できません・・・";
		}
	}
	@Override
	public String getSummarizedResponse(Packet packet) {
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		byte[] data = (packet.getDecodedData().length > 0) ?
				packet.getDecodedData() : packet.getModifiedData();

		try {
			if (data.length == 0) throw new Exception();
			Http http = new Http(data);
			if (http.getStatusCode() == null)
				return getSummarizedMessage(encodeMQTT(data));
			return http.getStatusCode();
		} catch (Exception e) {
			return "Headlineを生成できません・・・";
		}
	}

	private String getSummarizedMessage(byte[] data) {
		try {
			MQMessage message = MQParser.decode(Unpooled.copiedBuffer(data));
			String cmd = message.getType().toString();
			Integer msgId = null;
			switch (message.getType()) {
				// Has Message ID
				case PUBLISH:
					msgId = ((Publish)message).getPacketID();
					break;
				case PUBACK:
					msgId = ((Puback)message).getPacketID();
					break;
				case PUBREC:
					msgId = ((Pubrec)message).getPacketID();
					break;
				case PUBREL:
					msgId = ((Pubrel)message).getPacketID();
					break;
				case PUBCOMP:
					msgId = ((Pubcomp)message).getPacketID();
					break;
				case SUBSCRIBE:
					msgId = ((Subscribe)message).getPacketID();
					break;
				case SUBACK:
					msgId = ((Suback)message).getPacketID();
					break;
				case UNSUBSCRIBE:
					msgId = ((Unsubscribe)message).getPacketID();
					break;
				case UNSUBACK:
					msgId = ((Unsuback)message).getPacketID();
					break;
			}
			return msgId != null? msgId + ": " + cmd : cmd;
		} catch (Exception e) {
			e.printStackTrace();
			return "Failed to Parse as MQTT Protocol";
		}
	}
}
