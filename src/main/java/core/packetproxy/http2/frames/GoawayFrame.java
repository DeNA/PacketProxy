package packetproxy.http2.frames;

import java.nio.ByteBuffer;

public class GoawayFrame extends Frame {

    static protected Type TYPE = Type.GOAWAY;
    
    private int lastStreamId;
    private int errorCode;

    public GoawayFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
    }

	public GoawayFrame(byte[] data) throws Exception {
		super(data);
		parsePayload();
	}
	
	private void parsePayload() throws Exception {
		ByteBuffer bb = ByteBuffer.allocate(4096);
		bb.put(payload);
		bb.flip();
		lastStreamId = bb.getInt();
		errorCode = bb.getInt();
	}
	
	public int getLastStreamId() {
		return lastStreamId;
	}
	
	public int getErrorCode() {
		return errorCode;
	}

	@Override
	public String toString() {
		return super.toString() + ", last stream id=" + lastStreamId + ",error code=" + errorCode;
	}
}