package packetproxy.http2.frames;

import java.io.ByteArrayInputStream;

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

    protected int length;
    protected Type type;
    protected int flags;
    protected int streamId;
    protected byte[] payload;
    
    public static void main(String[] args) throws Exception {
    	byte[] frame = Hex.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
    	Frame fb = new Frame(frame);
    	System.out.println(fb.toString());
    }
    
    public Frame(byte[] data) throws Exception {
    	ByteArrayInputStream bais = new ByteArrayInputStream(data);
    	byte[] buffer = new byte[128];

    	bais.read(buffer, 0, 3);
    	length = (int)(buffer[0] << 16 | buffer[1] << 8 | buffer[2]);
    	bais.read(buffer, 0, 1);
    	type = Type.values()[(int)buffer[0]];
    	bais.read(buffer, 0, 1);
    	flags = (int)buffer[0];
    	bais.read(buffer, 0, 4);
    	streamId = (int)((buffer[0] & 0x7f) << 24 | buffer[1] << 16 | buffer[2] << 8 | buffer[3]);
    	payload = new byte[length];
    	bais.read(payload);
    }
    
    public int getLength() { return length; }
    public Type getType() { return type; }
    public int getFlags() { return flags; }
    public int getStreamId() { return streamId; }
    public byte[] getPayload() { return payload; }
    
    @Override
    public String toString() {
    	return String.format("length=%d, type=%s, flags=%d, streamId=%d, data=%s", length, type.name(), flags, streamId, new String(Hex.encodeHex(payload)));
    }
    
}
