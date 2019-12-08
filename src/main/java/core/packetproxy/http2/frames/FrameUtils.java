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
package packetproxy.http2.frames;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.http2.hpack.HpackDecoder;

public class FrameUtils {

	static public byte[] PREFACE; /* PRI * HTTP/2.0 .... */
	static public byte[] SETTINGS;
	static public byte[] END_SETTINGS;
	static public byte[] WINDOW_UPDATE;
	
	static {
		try {
			PREFACE = Hex.decodeHex("505249202a20485454502f322e300d0a0d0a534d0d0a0d0a".toCharArray());
			SETTINGS = Hex.decodeHex("0000120400000000000003000003e800045fffffff000200000000".toCharArray()); // header_table:4096, header_list:unlimited, window_size:unlimited
			END_SETTINGS = Hex.decodeHex("000000040100000000".toCharArray());
			WINDOW_UPDATE = Hex.decodeHex("0000040800000000005fffffff".toCharArray()); // connection_window_size:unlimited
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	static public boolean isPreface(byte[] frameData) {
		return (PREFACE.length > frameData.length) ? false : Arrays.equals(frameData, 0, PREFACE.length, PREFACE, 0, PREFACE.length);
	}

	 /* バイト列からHTTP2の1フレームを切り出す */
	static public int checkDelimiter(byte[] data) throws Exception {
		if (data.length < 9) {
			return -1;
		}
		if (isPreface(data)) {
			return PREFACE.length;
		}
		int headerSize = 9;
		int payloadSize = ((data[0] & 0xff) << 16 | (data[1] & 0xff) << 8 | (data[2] & 0xff));
		int expectedSize = headerSize + payloadSize;
		if (data.length < expectedSize) {
			return -1;
		}
		return expectedSize;
	}
	
	static public byte[] toByteArray(List<Frame> frames) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Frame frame: frames) {
			out.write(frame.toByteArray());
		}
		return out.toByteArray();
	}

	static public List<Frame> parseFrames(byte[] frames) throws Exception {
		return parseFrames(frames, null);
	}

	static public List<Frame> parseFrames(byte[] frames, HpackDecoder hpackDecoder) throws Exception {
		List<Frame> frameList = new LinkedList<Frame>();
		while (frames != null && frames.length > 0) {
			int delim = checkDelimiter(frames);
			byte[] frame = ArrayUtils.subarray(frames, 0, delim);
			frames = ArrayUtils.subarray(frames, delim, frames.length);
			if (isPreface(frame) == false) {
				frameList.add(FrameFactory.create(frame, hpackDecoder));
			}
		}
		return frameList;
	}

}
