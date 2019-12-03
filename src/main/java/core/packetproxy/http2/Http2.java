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
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.http2.hpack.HpackDecoder;
import org.eclipse.jetty.http2.hpack.HpackEncoder;

import packetproxy.http.Http;
import packetproxy.http2.frames.DataFrame;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameFactory;
import packetproxy.http2.frames.GoawayFrame;
import packetproxy.http2.frames.HeadersFrame;
import packetproxy.http2.frames.RstStreamFrame;
import packetproxy.http2.frames.SettingsFrame;
import packetproxy.http2.frames.SettingsFrame.SettingsFrameType;
import packetproxy.http2.frames.WindowUpdateFrame;

public class Http2
{
	static private byte[] PREFACE; /* PRI * HTTP/2.0 .... */
	static private byte[] SETTINGS;
	static private byte[] WINDOW_UPDATE;
	
	public enum Http2Type {
		PROXY_CLIENT, PROXY_SERVER, RESEND_CLIENT
	}
	
	static {
		try {
			PREFACE = Hex.decodeHex("505249202a20485454502f322e300d0a0d0a534d0d0a0d0a".toCharArray());
			SETTINGS = Hex.decodeHex("00001204000000000000030000006400045fffffff000200000000".toCharArray()); // header_table:4096, header_list:unlimited, window_size:unlimited
			WINDOW_UPDATE = Hex.decodeHex("0000040800000000005fffffff".toCharArray()); // connection_window_size:unlimited
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	static byte[] getPreface() {
		return PREFACE;
	}

	static private boolean isPreface(byte[] frameData) {
		return (PREFACE.length > frameData.length) ? false : Arrays.equals(frameData, 0, PREFACE.length, PREFACE, 0, PREFACE.length);
	}
	
	/**
	 *  バイト列から1フレームを切り出す
	 */
	static public int parseFrameDelimiter(byte[] data) throws Exception {
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

	/** 
	 * ユーザが利用するユーティリティ関数。
	 * フレーム列(HEADER + DATA)をHTTPバイトデータに変換する
	 * 変換後のHTTPデータは、元のフレームに完全に戻すための情報をヘッダに付与すること（例：ストリーム番号）
	 */
	
	private HpackEncoder hpackEncoder = new HpackEncoder(4096, 65536);
	private HpackDecoder hpackDecoder;
	private Map<Integer, List<Frame>> bufferedHttpStreams = new HashMap<>();
	private List<Frame> httpStreams = new LinkedList<>();
	private List<Frame> otherStreams = new LinkedList<>();
	private FlowControlManager flowControlManager = new FlowControlManager();
	private boolean alreadySentPreface = false;
	private boolean alreadySentSettingsWindowupdate = false;
	private boolean compatHttp1 = false;

	public Http2() throws Exception {
	}
	
	public Http2(Http2Type type, boolean compatHttp1) throws Exception {
		this(type);
		this.compatHttp1 = compatHttp1;
	}
	
	public Http2(Http2Type type) throws Exception {
		switch (type) {
		case PROXY_CLIENT:
			alreadySentPreface = false;
			alreadySentSettingsWindowupdate = false;
			break;
		case PROXY_SERVER:
			alreadySentPreface = true;
			alreadySentSettingsWindowupdate = false;
			break;
		case RESEND_CLIENT:
			alreadySentPreface = false;
			alreadySentSettingsWindowupdate = false;
			break;
		}
	}

	public List<Frame> parseFrames(byte[] frames) throws Exception {
		List<Frame> frameList = new LinkedList<Frame>();
		while (frames != null && frames.length > 0) {
			int delim = Http2.parseFrameDelimiter(frames);
			byte[] frame = ArrayUtils.subarray(frames, 0, delim);
			frames = ArrayUtils.subarray(frames, delim, frames.length);
			if (isPreface(frame) == false) {
				frameList.add(FrameFactory.create(frame, hpackDecoder));
			}
		}
		return frameList;
	}

	public byte[] httpToFrames(byte[] httpData) throws Exception {
		if (compatHttp1) {
			ByteArrayOutputStream framesData = new ByteArrayOutputStream();
			Http http = new Http(httpData);
			/* HEADERS */
			byte[] headersFrame = new HeadersFrame(http, hpackEncoder).toByteArray();
			framesData.write(headersFrame);
			/* DATA */
			if (http.getBody().length > 0) {
				byte[] dataFrame = new DataFrame(http).toByteArray();
				framesData.write(dataFrame);
			}
			return framesData.toByteArray();

		} else {
			Http http = new Http(httpData);
			if (http.getHeader().getHeader("server").isPresent()) { /* header frame */
				HeadersFrame frame = new HeadersFrame(http, hpackEncoder);
				frame.setFlags(frame.getFlags() & ~0x01);
				byte[] headersFrame = frame.toByteArray();
				return headersFrame;
			} else { /* data frame */
				byte[] dataFrame = new DataFrame(http).toByteArrayTest();
				return dataFrame;
			}
		}
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
	public void writeFrame(byte[] framesData) throws Exception {
		for (Frame frame : parseFrames(framesData)) {
			if (frame instanceof HeadersFrame) {
				HeadersFrame headersFrame = (HeadersFrame)frame;
				if (compatHttp1) {
					if ((headersFrame.getFlags() & 0x01) > 0) {
						httpStreams.add(headersFrame);
					} else {
						addFrameToBufferedStream(bufferedHttpStreams, headersFrame);
					}
				} else {
					httpStreams.add(headersFrame);
				}
				//System.out.println("HeadersFrame:" + headersFrame);
			} else if (frame instanceof DataFrame) {
				if (compatHttp1) {
					addFrameToBufferedStream(bufferedHttpStreams, frame);
					if ((frame.getFlags() & 0x01) > 0) {
						httpStreams.addAll(bufferedHttpStreams.get(frame.getStreamId()));
						bufferedHttpStreams.remove(frame.getStreamId());
					}
				} else {
					httpStreams.add(frame);
				}
			} else if (frame instanceof SettingsFrame) {
				SettingsFrame settingsFrame = (SettingsFrame)frame;
				flowControlManager.setInitialWindowSize(settingsFrame);
				if ((settingsFrame.getFlags() & 0x1) == 0) {
					int header_table_size = settingsFrame.get(SettingsFrameType.SETTINGS_HEADER_TABLE_SIZE);
					int header_list_size = settingsFrame.get(SettingsFrameType.SETTINGS_MAX_HEADER_LIST_SIZE);
					hpackDecoder = new HpackDecoder(header_table_size, header_list_size);
				}
				//System.out.println("SettingsFrame:" + settingsFrame);
			} else if (frame instanceof GoawayFrame) {
				GoawayFrame goAwayFrame = (GoawayFrame)frame;
				if (goAwayFrame.getErrorCode() != 0) { // 0 is NO_ERROR
					System.err.println("GoAway:" + goAwayFrame);
				}
			} else if (frame instanceof RstStreamFrame) {
				RstStreamFrame rstFrame = (RstStreamFrame)frame;
				if (rstFrame.getErrorCode() != 0) { // 0 is NO_ERROR
					System.err.println("RstStream:" + rstFrame);
				}
				otherStreams.add(rstFrame);
			} else if (frame instanceof WindowUpdateFrame) {
				WindowUpdateFrame windowUpdateFrame = (WindowUpdateFrame)frame;
				flowControlManager.appendWindowSize(windowUpdateFrame);
				//System.out.println("WindowUpdate:" + windowUpdateFrame.getWindowSize());
			} else {
				otherStreams.add(frame);
			}
		}
	}
	
	/**
	 * HTTP以外のフレーム（例:PING等）を読み出す。
	 * readClientFrameと分ける理由は、GUIに表示せず、直接サーバに送信したいため。
	 * 送信すべきフレームがなければ、nullを返す。
	 */
	public byte[] readControlFrames() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (alreadySentPreface == false) {
			baos.write(PREFACE);
			alreadySentPreface = true;
		}
		if (alreadySentSettingsWindowupdate == false) {
			baos.write(SETTINGS);
			baos.write(WINDOW_UPDATE);
			alreadySentSettingsWindowupdate = true;
		}
		for (Frame frame : otherStreams) {
			baos.write(frame.toByteArray());
		}
		otherStreams.clear();
		return baos.toByteArray();
	}

	/**
	 * HTTPデータを読み出す。
	 * HTTPを構成するフレームがまだ溜まっていなかったら、nullを返す。
	 * ユーザはhttpToFrames()により元に戻してサーバに送信することになる。
	 */
	public byte[] readHttp() throws Exception {
		if (httpStreams.size() == 0) {
			return null;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (Frame frame : httpStreams) {
			if (compatHttp1) {
				if (frame instanceof HeadersFrame) {
					baos.write(frame.toHttp1());
				} else if (frame instanceof DataFrame) {
					baos.write(frame.getPayload());
				}
			} else {
				baos.write(frame.toHttp1());
			}
		}
		httpStreams.clear();
		return baos.toByteArray();
	}

	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	public void putToFlowControlledQueue(byte[] frameData) throws Exception {
		baos.write(frameData);
		int length = 0;
		while ((length = parseFrameDelimiter(baos.toByteArray())) > 0) {
			byte[] frame = ArrayUtils.subarray(baos.toByteArray(), 0, length);
			byte[] remaining = ArrayUtils.subarray(baos.toByteArray(), length, baos.size());
			baos.reset();
			baos.write(remaining);
			flowControlManager.write(new Frame(frame));
		}
	}
	public InputStream getFlowControlledInputStream() {
		return flowControlManager.getInputStream();
	}
	
}
