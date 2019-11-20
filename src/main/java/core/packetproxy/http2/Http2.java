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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;

import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;
import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameFactory;
import packetproxy.http2.frames.HeadersFrame;

public class Http2
{
	private static byte[] PREFACE; /* PRI * HTTP/2.0 .... */
	
	static {
		try {
			PREFACE = Hex.decodeHex("505249202a20485454502f322e300d0a0d0a534d0d0a0d0a".toCharArray());
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private static boolean isPreface(byte[] frameData) {
		return (PREFACE.length > frameData.length) ? false : Arrays.equals(frameData, 0, PREFACE.length, PREFACE, 0, PREFACE.length);
	}
	
	public static List<Frame> parseFrame(byte[] frames) throws Exception {
		List<Frame> frameList = new LinkedList<Frame>();
		while (frames != null && frames.length > 0) {
			int delim = Http2.parseFrameDelimiter(frames);

			byte[] frame = ArrayUtils.subarray(frames, 0, delim);
			frames = ArrayUtils.subarray(frames, delim, frames.length);

			if (isPreface(frame) == false) {
				frameList.add(FrameFactory.create(frame));
			}
		}
		return frameList;
	}

	/**
	 *  バイト列から1フレームを切り出す
	 */
	public static int parseFrameDelimiter(byte[] data) throws Exception {
		if (isPreface(data)) {
			return PREFACE.length;
		} else {
			int headerSize = 9;
			int payloadSize = ((data[0] & 0xff) << 16 | (data[1] & 0xff) << 8 | (data[2] & 0xff));
			return headerSize + payloadSize;
		}
	}

	/** 
	 * ユーザが利用するユーティリティ関数。
	 * フレーム列(HEADER + DATA)をHTTPバイトデータに変換する
	 * 変換後のHTTPデータは、元のフレームに完全に戻すための情報をヘッダに付与すること（例：ストリーム番号）
	 */
	public static byte[] framesToHttp(byte[] frames) throws Exception {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		for (Frame frame : Http2.parseFrame(frames)) {
			buf.write(frame.toHttp1());
		}
		return buf.toByteArray();
	}
	public static byte[] httpToFrames(byte[] httpData) throws Exception {
		Http http = new Http(httpData);
		HttpHeader headers = http.getHeader();
		for (HeaderField a : headers.getFields()) {
			System.out.println(a.getName());
			System.out.println(a.getValue());
		}
		return "".getBytes();
	}
	
	private ByteArrayOutputStream clientStream;
	private ByteArrayOutputStream serverStream;
	private Map<Integer, List<Frame>> bufferedHttpClientStreams = new HashMap<>();
	private Map<Integer, List<Frame>> bufferedHttpServerStreams = new HashMap<>();
	private List<Frame> httpClientStreams = new LinkedList<>();
	private List<Frame> httpServerStreams = new LinkedList<>();
	private List<Frame> otherClientStreams = new LinkedList<>();
	private List<Frame> otherServerStreams = new LinkedList<>();
	
	public Http2() throws Exception {
		clientStream = new ByteArrayOutputStream();
		serverStream = new ByteArrayOutputStream();
	}
	
	private void addFrameToBufferedStream(Map<Integer,List<Frame>> bufferdStream, Frame frame) {
		List<Frame> stream = bufferdStream.get(frame.getStreamId());
		if (stream == null) {
			stream = new LinkedList<Frame>();
			bufferdStream.put(frame.getStreamId(), stream);
		}
		stream.add(frame);
	}

	/**
	 * クライアントリクエストフレームをHTTP2モジュールに渡す。
	 * 本モジュールは、フレームを解析しストリームとして管理する。
	 */
	public void writeClientFrame(byte[] framesData) throws Exception {
		for (Frame frame : Http2.parseFrame(framesData)) {
			if (frame instanceof HeadersFrame) {
				HeadersFrame headersFrame = (HeadersFrame)frame;
				String contentLengthStr = headersFrame.getHttpFields().get("content-length");
				if (contentLengthStr == null || Integer.parseInt(contentLengthStr) == 0) {
					httpClientStreams.add(frame);
				} else {
					addFrameToBufferedStream(bufferedHttpClientStreams, frame);
				}
			} else if (frame instanceof DataFrame) {
				addFrameToBufferedStream(bufferedHttpClientStreams, frame);
				if ((frame.getFlags() & 0x01) > 0) {
					httpClientStreams.addAll(bufferedHttpClientStreams.get(frame.getStreamId()));
					bufferedHttpClientStreams.remove(frame.getStreamId());
				}
			} else {
				otherClientStreams.add(frame);
			}
		}
		//System.out.println(httpClientStreams.toString());
		//System.out.println(otherClientStreams.toString());
	}
	public void writeServerFrame(byte[] frames) throws Exception {
		serverStream.write(frames);
	}
	
	/**
	 * クライアントリクエストのHTTP以外のフレーム（例:SETTING）を読み出す。
	 * readClientFrameと分ける理由は、GUIに表示せず、直接サーバに送信したいため。
	 * 送信すべきフレームがなければ、nullを返す。
	 */
	public byte[] readClientControlFrames() throws Exception {
		return null;
	}
	public byte[] readServerControlFrames() throws Exception {
		return null;
	}

	/**
	 * クライアントリクエストのHTTPデータフレーム(HEADER+DATA)を読み出す。
	 * HTTPを構成するフレームがまだ溜まっていなかったら、nullを返す。
	 * ユーザは、framesToHttp()を使ってHttpに戻しGUIに表示し、httpToFramesにより元に戻してサーバに送信することになる。
	 */
	public byte[] readClientFrames() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (Frame frame : httpClientStreams) {
			baos.write(frame.toByteArray());
		}
		httpClientStreams.clear();
		return baos.toByteArray();
	}
	public byte[] readServerFrames() throws Exception {
		byte[] data = serverStream.toByteArray();
		serverStream.reset();
		return data;
	}
}
