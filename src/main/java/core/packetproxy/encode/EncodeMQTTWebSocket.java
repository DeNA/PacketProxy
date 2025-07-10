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
import packetproxy.http.Http;
import packetproxy.model.Packet;

public class EncodeMQTTWebSocket extends EncodeHTTPWebSocket {

	protected boolean binary_start = false;

	public EncodeMQTTWebSocket(String ALPN) throws Exception {
		super(ALPN);
	}

	@Override
	public String getName() {
		return "MQTTv3.1 over WebSocket";
	}

	@Override
	public String getContentType(byte[] input_data) throws Exception {
		if (binary_start) {

			return "WebSocket";
		} else {

			Http http = Http.create(input_data);
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

	@Override
	public byte[] decodeWebsocketRequest(byte[] input) throws Exception {
		return decodeMQTT(input);
	}

	public byte[] encodeWebsocketRequest(byte[] input) throws Exception {
		return encodeMQTT(input);
	}

	public byte[] decodeWebsocketResponse(byte[] input) throws Exception {
		return decodeMQTT(input);
	}

	public byte[] encodeWebsocketResponse(byte[] input) throws Exception {
		return encodeMQTT(input);
	}

	@Override
	public String getSummarizedRequest(Packet packet) {
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) {

			return "";
		}
		byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();

		try {

			if (data.length == 0)
				throw new Exception();
			Http http = Http.create(data);
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
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) {

			return "";
		}
		byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();

		try {

			if (data.length == 0)
				throw new Exception();
			Http http = Http.create(data);
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
				case PUBLISH :
					msgId = ((Publish) message).getPacketID();
					break;
				case PUBACK :
					msgId = ((Puback) message).getPacketID();
					break;
				case PUBREC :
					msgId = ((Pubrec) message).getPacketID();
					break;
				case PUBREL :
					msgId = ((Pubrel) message).getPacketID();
					break;
				case PUBCOMP :
					msgId = ((Pubcomp) message).getPacketID();
					break;
				case SUBSCRIBE :
					msgId = ((Subscribe) message).getPacketID();
					break;
				case SUBACK :
					msgId = ((Suback) message).getPacketID();
					break;
				case UNSUBSCRIBE :
					msgId = ((Unsubscribe) message).getPacketID();
					break;
				case UNSUBACK :
					msgId = ((Unsuback) message).getPacketID();
					break;
			}
			return msgId != null ? msgId + ": " + cmd : cmd;
		} catch (Exception e) {

			e.printStackTrace();
			return "Failed to Parse as MQTT Protocol";
		}
	}
}
