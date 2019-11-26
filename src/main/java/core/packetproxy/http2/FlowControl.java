package packetproxy.http2;

import java.io.ByteArrayOutputStream;

import org.apache.commons.lang3.ArrayUtils;

public class FlowControl {

	private int windowSize;
	private ByteArrayOutputStream queue;

	public FlowControl(int initialWindowSize) {
		windowSize = initialWindowSize;
		queue = new ByteArrayOutputStream();
	}
	
	public void appendWindowSize(int appendWindowSize) {
		windowSize += appendWindowSize;
	}
	
	public void enqueue(byte[] data) throws Exception {
		queue.write(data);
	}

	public byte[] dequeue() throws Exception {
		int length = Math.min(queue.size(), windowSize);
		windowSize = windowSize - length;
		byte[] data = ArrayUtils.subarray(queue.toByteArray(), 0, length);
		byte[] remaining = ArrayUtils.subarray(queue.toByteArray(), length, queue.size());
		queue.reset();
		queue.write(remaining);
		return data;
	}
	
	public int size() {
		return queue.size();
	}

}
