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
    static protected byte FLAG_EXTRA = 0x40; /* internal use only */

    protected int length = 9;
    protected Type type = Type.Unassigned;
    protected int flags = 0;
    protected int streamId = 0;
    protected byte[] payload = new byte[]{};
    protected byte[] origPayload = new byte[]{};
    protected byte[] extra = new byte[]{}; /* internal use only */
    
    protected Frame() throws Exception {
    }
    
    public Frame(Frame frame) throws Exception {
    	length = frame.length;
    	type = frame.type;
    	flags = frame.flags;
    	streamId = frame.streamId;
    	payload = frame.payload;
    	origPayload = frame.origPayload;
    	extra = frame.extra;
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
    	splitExtraFromPayload();
    }
    
    public int getLength() { return length; }
    public Type getType() { return type; }
    public int getFlags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }
    public int getStreamId() { return streamId; }
    public byte[] getPayload() { return payload; }
	public byte[] getOrigPayload() throws Exception { return this.origPayload; }
	public byte[] getExtra() throws Exception { return this.extra; }
    
    public byte[] toByteArrayWithoutExtra() throws Exception {
    	ByteBuffer bb = ByteBuffer.allocate(origPayload.length + 9);
    	bb.put((byte)((origPayload.length >>> 16) & 0xff));
    	bb.put((byte)((origPayload.length >>> 8) & 0xff));
    	bb.put((byte)((origPayload.length) & 0xff));
    	bb.put((byte)(type.ordinal() & 0xff));
    	bb.put((byte)(flags & ~FLAG_EXTRA));
    	bb.putInt(streamId);
    	bb.put(origPayload, 0, origPayload.length);
    	byte[] array = new byte[bb.position()];
    	bb.flip();
    	bb.get(array);
    	return array;
    }
    
    public byte[] toByteArray() throws Exception {
    	ByteBuffer bb = ByteBuffer.allocate(payload.length + 9);
    	bb.put((byte)((payload.length >>> 16) & 0xff));
    	bb.put((byte)((payload.length >>> 8) & 0xff));
    	bb.put((byte)((payload.length) & 0xff));
    	bb.put((byte)(type.ordinal() & 0xff));
    	bb.put((byte)(flags));
    	bb.putInt(streamId);
    	bb.put(payload, 0, payload.length);
    	byte[] array = new byte[bb.position()];
    	bb.flip();
    	bb.get(array);
    	return array;
    }
    
    public void removeExtra() {
		if ((flags & FLAG_EXTRA) == 0) {
			return;
		}
		ByteBuffer payloadBuf = ByteBuffer.allocate(this.payload.length);
		payloadBuf.put(this.payload);
		payloadBuf.flip();
		int extraLen = payloadBuf.getInt();
		int origPayloadLen = this.payload.length - extraLen - 4;
		this.origPayload = new byte[origPayloadLen];
		payloadBuf.get(this.origPayload);
		this.payload = this.origPayload;
		this.length = this.payload.length;
		this.extra = new byte[]{};
		flags &= ~FLAG_EXTRA;
    }

	public void saveExtra(byte[] extra) throws Exception {
		ByteBuffer newPayload = ByteBuffer.allocate(this.origPayload.length + extra.length + 4);
		newPayload.putInt(extra.length);
		newPayload.put(this.origPayload);
		newPayload.put(extra);
		byte[] newPayloadArray =  new byte[newPayload.limit()];
		newPayload.flip();
		newPayload.get(newPayloadArray);
		this.payload = newPayloadArray;
		this.length = this.payload.length;
		this.extra = extra;
		this.flags |= FLAG_EXTRA;
	}

	public void saveOrigPayload(byte[] origPayload) throws Exception {
		if ((flags & FLAG_EXTRA) == 0) {
			this.payload = origPayload;
			this.origPayload = origPayload;
			this.length = origPayload.length;
		} else {
			ByteBuffer newPayload = ByteBuffer.allocate(origPayload.length + this.extra.length + 4);
			newPayload.putInt(this.extra.length);
			newPayload.put(origPayload);
			newPayload.put(this.extra);
			byte[] newPayloadArray =  new byte[newPayload.limit()];
			newPayload.flip();
			newPayload.get(newPayloadArray);
			this.payload = newPayloadArray;
			this.origPayload = origPayload;
			this.length = this.payload.length;
		}
	}
	
	private void splitExtraFromPayload() {
		if ((flags & FLAG_EXTRA) == 0) {
			this.origPayload = payload;
			this.extra = new byte[]{};
			return;
		}
		ByteBuffer payloadBuf = ByteBuffer.allocate(this.payload.length);
		payloadBuf.put(this.payload);
		payloadBuf.flip();
		int extraLen = payloadBuf.getInt();
		int origPayloadLen = this.payload.length - extraLen - 4;
		this.origPayload = new byte[origPayloadLen];
		this.extra = new byte[extraLen];
		payloadBuf.get(this.origPayload);
		payloadBuf.get(this.extra);
	}
    
    @Override
    public String toString() {
    	return String.format("length=%d, type=%s, flags=0x%x, streamId=%d", length, type.name(), flags, streamId);
    }
    
}
