package packetproxy.http2;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import packetproxy.http2.frames.Frame;

public class StreamManager {
	private Map<Integer, List<Frame>> streamMap = new HashMap<>();
	
	public StreamManager() {
	}
	
	public void write(Frame frame) {
		List<Frame> stream = streamMap.get(frame.getStreamId());
		if (stream == null) {
			stream = new LinkedList<Frame>();
			streamMap.put(frame.getStreamId(), stream);
		}
		stream.add(frame);
	}
	
	public List<Frame> read(int streamId) {
		return streamMap.get(streamId);
	}
	
	public Set<Map.Entry<Integer,List<Frame>>> entrySet() {
		return streamMap.entrySet();
	}
	
	public void clear(int streamId) {
		streamMap.remove(streamId);
	}
	
	public byte[] mergePayload(int streamId) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame : read(streamId)) {
			out.write(frame.getPayload());
		}
		return out.toByteArray();
	}
	
	public byte[] toByteArray(int streamId) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame : read(streamId)) {
			out.write(frame.toByteArray());
		}
		return out.toByteArray();
	}

}
