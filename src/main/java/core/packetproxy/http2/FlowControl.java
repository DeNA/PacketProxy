/*
 * Copyright 2019 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.http2;

import java.io.ByteArrayOutputStream;

import org.apache.commons.lang3.ArrayUtils;

import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameFactory;

public class FlowControl {

	private int streamId;
	private int windowSize;
	private ByteArrayOutputStream queue;
	private boolean end_flag;

	public FlowControl(int streamId, int initialWindowSize) {
		this.streamId = streamId;
		this.windowSize = initialWindowSize;
		queue = new ByteArrayOutputStream();
		end_flag = false;
	}
	
	public void appendWindowSize(int appendWindowSize) {
		synchronized (queue) {
			windowSize += appendWindowSize;
		}
	}
	
	public void enqueue(Frame frame) throws Exception {
		synchronized (queue) {
			queue.write(frame.getPayload());
			queue.flush();
			if ((frame.getFlags() & DataFrame.FLAG_END_STREAM) > 0) {
				end_flag = true;
			}
		}
	}

	public Stream dequeue(int connectionWindowSize) throws Exception {
		synchronized (queue) {
			int capacity = Math.min(windowSize, connectionWindowSize);
			if (capacity == 0) {
				System.err.println("[HTTP/2 FlowControl] running out of window.");
				return null;
			}
			int dataLen = Math.min(queue.size(), capacity);
			if (dataLen == 0) {
				//System.err.println("[HTTP/2 FlowControl] sending data is not found.");
				return null;
			}
			windowSize -= dataLen;
			byte[] data = ArrayUtils.subarray(queue.toByteArray(), 0, dataLen);
			byte[] remaining = ArrayUtils.subarray(queue.toByteArray(), dataLen, queue.size());
			queue.reset();
			queue.write(remaining);
			queue.flush();
			
			Stream stream = new Stream();
			
			while (data.length > 0) {

				int payloadLen = Math.min(data.length, 16384);
				byte[] payload = ArrayUtils.subarray(data, 0, payloadLen);
				data = ArrayUtils.subarray(data, payloadLen, data.length);

				int flags = 0x0;
				if (remaining.length == 0 && end_flag == true && data.length == 0) {
					flags = DataFrame.FLAG_END_STREAM;
				}

				Frame frame = FrameFactory.create(DataFrame.TYPE, flags, streamId, payload);

				stream.write(frame);
			}

			return stream;
		}
	}
	
	public int size() {
		synchronized (queue) {
			return queue.size();
		}
	}

}
