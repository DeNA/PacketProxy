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

	public byte[] toByteArrayWithoutExtra() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame : stream) {
			out.write(frame.toByteArrayWithoutExtra());
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
