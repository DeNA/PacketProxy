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

import static packetproxy.util.Logging.err;

import java.io.ByteArrayOutputStream;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameFactory;

@Getter
public class FlowControl {

	private int streamId;
	private int windowSize;
	private Frame headersFrame = null;
	private Frame grpcHeaderFrame = null;
	private boolean headersFrameSent = false;
	private boolean dataFrameSent = false;
	private boolean grpcHeadersFrameSent = false;
	private ByteArrayOutputStream queue;
	private boolean end_flag;
	private boolean empty_data_end_flag = false;

	public FlowControl(int streamId, int initialWindowSize) {
		this.streamId = streamId;
		this.windowSize = initialWindowSize;
		this.queue = new ByteArrayOutputStream();
		this.end_flag = false;
	}

	public void appendWindowSize(int appendWindowSize) {
		synchronized (queue) {
			windowSize += appendWindowSize;
		}
	}

	// 1回目のpushは、HeadersFrameとして扱う。
	// 2回目のpushは、gRPC通信の2nd HeadersFrameとして扱い、DataFrameの後に送信する
	public void pushHeadersFrame(Frame headersFrame) {
		if (this.headersFrame == null) {

			this.headersFrame = headersFrame;
		} else {

			this.grpcHeaderFrame = headersFrame;
		}
	}

	public void enqueue(Frame frame) throws Exception {
		synchronized (queue) {
			queue.write(frame.getPayload());
			queue.flush();
			if ((frame.getFlags() & DataFrame.FLAG_END_STREAM) > 0) {

				end_flag = true;
				if (queue.size() == 0) {

					empty_data_end_flag = true;
				}
			}
		}
	}

	public synchronized Stream dequeue(int connectionWindowSize) throws Exception {
		Stream stream = new Stream();

		// 最初にheadersFrameを送信する
		if (!this.headersFrameSent && this.headersFrame != null) {

			// Logging.log("[%d] HeadersFrame sent!\n", streamId);
			stream.write(this.headersFrame);
			this.headersFrameSent = true;
			return stream;
		}

		// データの送信が終わっていたら、grpcヘッダを送信する
		if (this.headersFrameSent && (this.dataFrameSent || queue.size() == 0) && !this.grpcHeadersFrameSent
				&& this.grpcHeaderFrame != null) {

			// Logging.log("[%d] gRPC HeadersFrame sent!\n", streamId);
			stream.write(this.grpcHeaderFrame);
			this.grpcHeadersFrameSent = true;
			return stream;
		}

		if (queue.size() == 0) {

			if (empty_data_end_flag) {

				empty_data_end_flag = false;
				int flags = DataFrame.FLAG_END_STREAM;
				Frame frame = FrameFactory.create(DataFrame.TYPE, flags, streamId, new byte[0]);
				stream.write(frame);
				return stream;
			}
			return null; /* no data */
		}

		int capacity = Math.min(windowSize, connectionWindowSize);
		if (capacity == 0) {

			err("[HTTP/2 FlowControl] try to send %d data, but running out of window (streamId: %d)", queue.size(),
					this.streamId);
			return null;
		}
		// 少しだけcapacityに余裕を持たせる
		if (capacity <= 3000) {

			return null;
		} else {

			capacity -= 3000;
		}
		int dataLen = Math.min(queue.size(), capacity);
		if (dataLen == 0) {

			err("[HTTP/2 FlowControl] sending data is not found (streamId: %d)", this.streamId);
			return null;
		}
		this.windowSize -= dataLen;
		byte[] data = ArrayUtils.subarray(queue.toByteArray(), 0, dataLen);
		byte[] remaining = ArrayUtils.subarray(queue.toByteArray(), dataLen, queue.size());
		queue.reset();
		queue.write(remaining);
		queue.flush();

		while (data.length > 0) {

			int payloadLen = Math.min(data.length, 16384);
			byte[] payload = ArrayUtils.subarray(data, 0, payloadLen);
			data = ArrayUtils.subarray(data, payloadLen, data.length);

			int flags = 0x0;
			if (remaining.length == 0 && end_flag && data.length == 0) {

				flags = DataFrame.FLAG_END_STREAM;
			}
			Frame frame = FrameFactory.create(DataFrame.TYPE, flags, streamId, payload);

			if (remaining.length == 0 && data.length == 0) {

				this.dataFrameSent = true;
			}

			stream.write(frame);
		}

		// データの送信が終わっていたら、grpcヘッダを送信する
		if (this.headersFrameSent && this.dataFrameSent && !this.grpcHeadersFrameSent && this.grpcHeaderFrame != null) {

			// Logging.log("[%d] gRPC HeadersFrame sent!\n", streamId);
			stream.write(this.grpcHeaderFrame);
			this.grpcHeadersFrameSent = true;
		}

		return stream;
	}

	public int size() {
		synchronized (queue) {
			return queue.size();
		}
	}
}
