package packetproxy.http2;

import java.io.ByteArrayOutputStream;

import org.apache.commons.lang3.ArrayUtils;

public class FlowControl {

	private int windowSize;
	private ByteArrayOutputStream queue;

	public FlowControl(int initialWindowSize) {
		this.windowSize = initialWindowSize;
		queue = new ByteArrayOutputStream();
	}
	
	public void appendWindowSize(int appendWindowSize) {
		synchronized (queue) {
			windowSize += appendWindowSize;
		}
	}
	
	public void enqueue(byte[] data) throws Exception {
		synchronized (queue) {
			queue.write(data);
		}
	}

	public byte[] dequeue(int connectionWindowSize) throws Exception {
		synchronized (queue) {
			int capacity = Math.min(windowSize, connectionWindowSize);
			if (capacity == 0) {
				System.err.println("[HTTP/2 FlowControl] running out of window.");
				return new byte[]{};
			}
			int length = Math.min(queue.size(), capacity);
			if (length == 0) {
				//System.err.println("[HTTP/2 FlowControl] sending data is not found.");
				return new byte[]{};
			}
			windowSize -= length;

			byte[] data = ArrayUtils.subarray(queue.toByteArray(), 0, length);
			byte[] remaining = ArrayUtils.subarray(queue.toByteArray(), length, queue.size());
			queue.reset();
			queue.write(remaining);
			return data;
		}
	}
	
	public int size() {
		synchronized (queue) {
			return queue.size();
		}
	}

}
