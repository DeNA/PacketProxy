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

import packetproxy.http.Http;
import packetproxy.http.HttpHeader;

public class DataFrame extends Frame {

    static protected Type TYPE = Type.DATA; 
    static byte FLAG_END_STREAM = 0x01;
    static byte FLAG_PADDED     = 0x08;
    
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
		super.payload = http.getBody();
		super.flags = FLAG_END_STREAM;
		super.type = TYPE;
		super.length = payload.length;
		
	}
	
	public boolean isEnd() {
		return (flags & FLAG_END_STREAM) > 0;
	}

	private void parsePayload() throws Exception {
		if ((flags & FLAG_PADDED) > 0) {
			System.err.println("[Error] padded DATA Frame is not supported yet.");
		}
	}

	@Override
    public byte[] toByteArray() throws Exception {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
    			bb.put((byte)0x0);
    		} else {
    			bb.put((byte)0x1);
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
	
	@Override
	public byte[] toHttp1() throws Exception {
		return payload;
	}
	
	@Override
	public String toString() {
		return super.toString() + new String(payload);
	}

}
