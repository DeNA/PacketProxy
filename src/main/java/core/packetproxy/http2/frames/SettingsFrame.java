package packetproxy.http2.frames;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class SettingsFrame extends Frame {

    static protected Type TYPE = Type.SETTINGS;
    static public enum SettingsFrameType {
    	RESERVED,
    	SETTINGS_HEADER_TABLE_SIZE,
    	SETTINGS_ENABLE_PUSH,
    	SETTINGS_MAX_CONCURRENT_STREAMS,
    	SETTINGS_INITIAL_WINDOW_SIZE,
    	SETTINGS_MAX_FRAME_SIZE,
    	SETTINGS_MAX_HEADER_LIST_SIZE,
    };
    static private int[] defaultValues = new int[] {
    	0,
    	4096,
    	1,
    	10,
    	65535,
    	16884,
    	65536,
    };
    
    private Map<SettingsFrameType, Integer> values = new HashMap<>();
    
    public SettingsFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
    }

	public SettingsFrame(byte[] data) throws Exception {
		super(data);
		parsePayload();
	}
	
	private void parsePayload() throws Exception {
		ByteBuffer bb = ByteBuffer.allocate(4096);
		bb.put(payload);
		bb.flip();
		SettingsFrameType[] settingsFrameTypes = SettingsFrameType.values();
		for(int length = 0; length < bb.limit(); length += 6) {
			short l = bb.getShort();
			if(0x00<=l && l<settingsFrameTypes.length){
				SettingsFrameType type = settingsFrameTypes[l];
				int data = bb.getInt();
				values.put(type, data);
			}
			//System.out.println(String.format("%s: %d", type, data));
		}
	}

	public int get(SettingsFrameType type) {
		return values.containsKey(type) ? values.get(type) : defaultValues[type.ordinal()];
	}
	
	public void set(SettingsFrameType type, int value) {
		values.put(type, value);
	}
	
	@Override
	public String toString() {
		return super.toString() + values;
	}
}