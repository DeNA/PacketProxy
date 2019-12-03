package packetproxy.http2.frames;

import java.nio.ByteBuffer;

public class RstStreamFrame extends Frame {

    static protected Type TYPE = Type.RST_STREAM;
    
    private int errorCode;

    public RstStreamFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
    }

	public RstStreamFrame(byte[] data) throws Exception {
		super(data);
		parsePayload();
	}
	
	private void parsePayload() throws Exception {
		ByteBuffer bb = ByteBuffer.allocate(4096);
		bb.put(payload);
		bb.flip();
		errorCode = bb.getInt();
	}
	
	public int getErrorCode() {
		return errorCode;
	}

	@Override
	public String toString() {
		return super.toString() + ", error code=" + errorCode;
	}
}