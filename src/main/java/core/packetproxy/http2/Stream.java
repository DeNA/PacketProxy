package packetproxy.http2;

import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.List;

import packetproxy.http2.frames.Frame;

public class Stream {
	
	private List<Frame> stream = new LinkedList<>();
	
	public void write(Frame frame) {
		stream.add(frame);
	}
	
	public byte[] toByteArray() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame : stream) {
			out.write(frame.toByteArray());
		}
		return out.toByteArray();
	}

	public int payloadSize() throws Exception {
		int size = 0;
		for (Frame frame : stream) {
			size += frame.getLength();
		}
		return size;
	}
}
