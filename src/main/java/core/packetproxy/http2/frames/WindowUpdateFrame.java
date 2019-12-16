package packetproxy.http2.frames;

public class WindowUpdateFrame extends Frame {

    static protected Type TYPE = Type.WINDOW_UPDATE;
    
    private int window;

    public WindowUpdateFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
    }

	public WindowUpdateFrame(byte[] data) throws Exception {
		super(data);
		parsePayload();
	}
	
	private void parsePayload() throws Exception {
    	window = (int)((payload[0] & 0x7f) << 24 | (payload[1] & 0xff) << 16 | (payload[2] & 0xff) << 8 | (payload[3] & 0xff));
	}
	
	public int getWindowSize() {
		return window;
	}

	@Override
	public String toString() {
		return super.toString() + String.valueOf(window);
	}
}