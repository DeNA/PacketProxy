package packetproxy.http2.frames;

import java.io.ByteArrayInputStream;

import org.apache.commons.codec.binary.Hex;

public class FrameBase {
    private int length;
    private int type;
    private int flags;
    private int streamId;
    private byte[] payload;
    
    public static void main(String[] args) throws Exception {
    	byte[] frame = Hex.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
    	FrameBase fb = new FrameBase(frame);
    	System.out.println(fb.toString());
    }
    
    public FrameBase(byte[] data) throws Exception {
    	ByteArrayInputStream bais = new ByteArrayInputStream(data);
    	byte[] buffer = new byte[1024];

    	bais.read(buffer, 0, 3);
    	length = (int)(buffer[0] << 16 | buffer[1] << 8 | buffer[2]);
    	bais.read(buffer, 0, 1);
    	type = (int)buffer[0];
    	bais.read(buffer, 0, 1);
    	flags = (int)buffer[0];
    	bais.read(buffer, 0, 4);
    	streamId = (int)((buffer[0] & 0x7f) << 24 | buffer[1] << 16 | buffer[2] << 8 | buffer[3]);
    	payload = new byte[length];
    	bais.read(payload);
    }
    
    @Override
    public String toString() {
    	return String.format("length=%d, type=%d, flags=%d, streamId=%d, data=%s", length, type, flags, streamId, new String(Hex.encodeHex(payload)));
    }
    
}
