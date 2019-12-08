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
import java.nio.ByteBuffer;

import org.apache.commons.lang3.ArrayUtils;

import packetproxy.http.Http;
import packetproxy.http.HttpHeader;

public class DataFrame extends Frame {

    static protected Type TYPE = Type.DATA; 
    static public byte FLAG_END_STREAM = (byte)0x01;
    static public byte FLAG_PADDED     = (byte)0x08;
    
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
		super.flags = FLAG_END_STREAM;
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
	
	@Override
    public byte[] toByteArray() throws Exception {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();

    	if (payload.length == 0) {
    		ByteBuffer bb = ByteBuffer.allocate(1024);
    		bb.put((byte)0);
    		bb.put((byte)0);
    		bb.put((byte)0);
    		bb.put((byte)(type.ordinal() & 0xff));
   			bb.put((byte)(flags & 0xff));
    		bb.putInt(streamId);
    		byte[] array = new byte[bb.position()];
    		bb.flip();
    		bb.get(array);
    		baos.write(array);
    		return baos.toByteArray();
		}

    	int offset = 0;
    	for (int rest = payload.length; rest > 0; ) {
    		int blockLen = (rest > 8192 ? 8192 : rest);
    		rest = rest - blockLen;

    		ByteBuffer bb = ByteBuffer.allocate(blockLen + 1024);
    		bb.put((byte)((blockLen >>> 16) & 0xff));
    		bb.put((byte)((blockLen >>> 8) & 0xff));
    		bb.put((byte)((blockLen) & 0xff));
    		bb.put((byte)(type.ordinal() & 0xff));
    		if (rest > 0) {
    			bb.put((byte)(flags & ~FLAG_END_STREAM & 0xff));
    		} else {
    			bb.put((byte)(flags & 0xff));
    		}
    		bb.putInt(streamId);
    		bb.put(payload, offset, blockLen);
    		byte[] array = new byte[bb.position()];
    		bb.flip();
    		bb.get(array);
    		baos.write(array);

    		offset = offset + blockLen;
    	}
    	return baos.toByteArray();
    }

	public byte[] getHttp() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write("HTTP/2.0 200 OK\r\n".getBytes());
		baos.write(new String("X-PacketProxy-HTTP2-Stream-Id: " + streamId + "\r\n").getBytes());
		baos.write(new String("X-PacketProxy-HTTP2-Flags: " + flags + "\r\n").getBytes());
		baos.write("\r\n".getBytes());
		baos.write(payload);
		return baos.toByteArray();
	}
	
	@Override
	public String toString() {
		return super.toString();
	}

}
