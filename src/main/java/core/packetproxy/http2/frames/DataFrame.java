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
package packetproxy.http2.frames;

import java.io.ByteArrayOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;

public class DataFrame extends Frame {

	public static Type TYPE = Type.DATA;
	public static byte FLAG_END_STREAM = (byte) 0x01;
	public static byte FLAG_PADDED = (byte) 0x08;

	public DataFrame(int flags, int streamId, byte[] payload) {
		super(TYPE, flags, streamId, payload);
	}

	public DataFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
	}

	public DataFrame(byte[] data) throws Exception {
		super(data);
		parsePayload();
	}

	public DataFrame(Http http) throws Exception {
		super();

		HttpHeader headers = http.getHeader();
		super.streamId = Integer.parseInt(headers.getValue("X-PacketProxy-HTTP2-Stream-Id").orElse("0"));

		if (TYPE.ordinal() == Integer.parseInt(headers.getValue("X-PacketProxy-HTTP2-Type").orElse("0"))) {

			super.flags = Integer.parseInt(headers.getValue("X-PacketProxy-HTTP2-Flags").orElse("1"));
		} else {

			super.flags = FLAG_END_STREAM;
		}
		super.payload = http.getBody();
		super.origPayload = http.getBody();
		super.extra = new byte[]{};
		super.type = TYPE;
		super.length = payload.length;

	}

	private void parsePayload() throws Exception {
		if ((flags & FLAG_PADDED) > 0) {

			int padLen = (payload[0] & 0xff);
			payload = ArrayUtils.subarray(payload, 1, payload.length - padLen);
			flags &= ~FLAG_PADDED;
		}
	}

	@Override
	public byte[] toByteArrayWithoutExtra() throws Exception {
		return toByteArray();
	}

	public byte[] getHttp() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write("HTTP/2 200 OK\r\n".getBytes());
		baos.write(new String("X-PacketProxy-HTTP2-Type: " + TYPE.ordinal() + "\r\n").getBytes());
		baos.write(new String("X-PacketProxy-HTTP2-Stream-Id: " + super.streamId + "\r\n").getBytes());
		baos.write(new String("X-PacketProxy-HTTP2-Flags: " + super.flags + "\r\n").getBytes());
		baos.write("\r\n".getBytes());
		baos.write(payload);
		return baos.toByteArray();
	}

	@Override
	public String toString() {
		return super.toString();
	}

}
