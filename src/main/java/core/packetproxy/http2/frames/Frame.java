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

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import org.apache.commons.codec.binary.Hex;

public class Frame {
    public enum Type {
    	DATA,
    	HEADERS,
    	PRIORITY,
    	RST_STREAM,
    	SETTINGS,
    	PUSH_PROMISE,
    	PING,
    	GOAWAY,
    	WINDOW_UPDATE,
    	CONTINUATION,
    	ALTSVC,
    	Unassigned,
    	ORIGIN
    };
    
    static protected Type TYPE = Type.Unassigned; 

    protected int length;
    protected Type type;
    protected int flags;
    protected int streamId;
    protected byte[] payload;
    
    protected Frame() throws Exception {
    }
    
    public Frame(Frame frame) throws Exception {
    	length = frame.length;
    	type = frame.type;
    	flags = frame.flags;
    	streamId = frame.streamId;
    	payload = frame.payload;
    }
    
    public Frame(byte[] data) throws Exception {
    	ByteArrayInputStream bais = new ByteArrayInputStream(data);
    	byte[] buffer = new byte[128];

    	bais.read(buffer, 0, 3);
    	length = (int)((buffer[0] & 0xff) << 16 | (buffer[1] & 0xff) << 8 | (buffer[2] & 0xff));
    	bais.read(buffer, 0, 1);
    	type = Type.values()[(int)buffer[0]];
    	bais.read(buffer, 0, 1);
    	flags = (int)buffer[0];
    	bais.read(buffer, 0, 4);
    	streamId = (int)((buffer[0] & 0x7f) << 24 | (buffer[1] & 0xff) << 16 | (buffer[2] & 0xff) << 8 | (buffer[3] & 0xff));
    	payload = new byte[length];
    	bais.read(payload);
    }
    
    public int getLength() { return length; }
    public Type getType() { return type; }
    public int getFlags() { return flags; }
    public int getStreamId() { return streamId; }
    public byte[] getPayload() { return payload; }
    
    public byte[] toByteArray() {
    	ByteBuffer bb = ByteBuffer.allocate(4096);
    	bb.put((byte)((payload.length >>> 16) & 0xff));
    	bb.put((byte)((payload.length >>> 8) & 0xff));
    	bb.put((byte)(payload.length & 0xff));
    	bb.put((byte)(type.ordinal() & 0xff));
    	bb.put((byte)(flags & 0xff));
    	bb.putInt(streamId);
    	bb.put(payload);
    	byte[] array = new byte[bb.position()];
    	bb.flip();
    	bb.get(array);
    	return array;
    }
    
    public byte[] toHttp1() throws Exception {
    	return null;
    }
    
    @Override
    public String toString() {
    	return String.format("length=%d, type=%s, flags=%d, streamId=%d, data=%s", length, type.name(), flags, streamId, new String(Hex.encodeHex(payload)));
    }
    
}
