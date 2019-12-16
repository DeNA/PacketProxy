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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import packetproxy.model.Packet;
import packetproxy.util.PacketProxyUtility;

public class EncodeMQTT extends Encoder {

	public EncodeMQTT(String ALPN) throws Exception {
		super(ALPN);
	}

	@Override
	public String getName() {
		return "MQTTv3.1";
	}

	@Override
	public int checkDelimiter(byte[] input_data) throws Exception {
		int length = 0;
		byte digit;
		int multiplier = 1;
		int i = 0;
		do {
			digit = input_data[++i];
			length += (digit & 0x7F) * multiplier;
			multiplier *= 0x80;
		} while ((digit & 0x80) != 0 && i < 4);

		// MQTT Header(1+i) + Body Length
		return 1 + i + length;
	}

	@Override
	public byte[] encodeClientRequest(byte[] input_data) throws Exception {
		return encode(input_data);
	}
	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		return decode(input_data);
	}
	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		return encode(input_data);
	}
	@Override
	public byte[] decodeServerResponse(byte[] input_data) throws Exception {
		return decode(input_data);
	}
	@Override
	public String getSummarizedRequest(Packet packet) {
		return getSummarizedMessage(packet);
	}
	@Override
	public String getSummarizedResponse(Packet packet) {
		return getSummarizedMessage(packet);
	}

	public static MQJsonParser parser = new MQJsonParser();
	private byte[] encode(byte[] b) throws Exception {
		MQMessage m = parser.messageObject(new String(b));
		return MQParser.encode(m).array();
	}
	private byte[] decode(byte[] b) throws Exception {
		MQMessage m = MQParser.decode(Unpooled.copiedBuffer(b));
		return parser.jsonString(m).getBytes();
	}
	private String getSummarizedMessage(Packet packet) {
		byte[] raw_data = (packet.getSentData().length > 0) ? packet.getSentData()
			: packet.getReceivedData();
		if (raw_data.length == 0) return "";

		try {
			MQMessage message = MQParser.decode(Unpooled.copiedBuffer(raw_data));
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

	/*
	public static void main(String args[]) {
		PacketProxyUtility util = PacketProxyUtility.getInstance();
		int length;

		byte[] b = java.util.Base64.getMimeDecoder().decode("EDAABE1RVFQEAgAeACQwYTg2ZDA5Ny1mOThkLTRkMjktOGUyMy1hOGUxMWM3MzA2ODY=");
		ByteBuf data = Unpooled.copiedBuffer(b);
		MQMessage message = MQParser.decode(data);

		MQJsonParser parser = new MQJsonParser();

		try {
			String json = parser.jsonString(message);
			util.packetProxyLog(json);
			util.packetProxyLog(parser.messageObject(json).toString());
		} catch (Exception e) {
			e.printStackTrace();
		}

		EncodeMQTT mqtt = new EncodeMQTT();
		try {
			byte[] test1 = {0x00, 0x1c};
			length = mqtt.checkDelimiter(test1);
			if (length != 30) {
				util.packetProxyLog(String.format("Failed Test1: 30 == %d\n", length));
				return;
			}

			byte[] test2 = {0x00, (byte) 0xc6, 0x09};
			length = mqtt.checkDelimiter(test2);
			if (length != 1225) {
				util.packetProxyLog(String.format("Failed Test2: 1222 == %d\n", length));
				return;
			}

			byte[] test3 = {0x00, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01};
			length = mqtt.checkDelimiter(test3);
			if (length != 2097157) {
				util.packetProxyLog(String.format("Failed Test3: 2097152 == %d\n", length));
				return;
			}

			byte[] test4 = {0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F};
			length = mqtt.checkDelimiter(test4);
			if (length != 268435460) {
				util.packetProxyLog(String.format("Failed Test4: 268435455 == %d\n", length));
				return;
			}

			byte[] test5 = {0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01};
			length = mqtt.checkDelimiter(test5);
			if (length != 268435460) {
				util.packetProxyLog(String.format("Failed Test5: 268435455 == %d\n", length));
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		util.packetProxyLog(String.format("Passed some tests."));
	}
	*/
}
