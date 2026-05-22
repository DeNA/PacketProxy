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
import java.util.List;
import org.eclipse.jetty.http2.hpack.HpackDecoder;
import org.eclipse.jetty.http2.hpack.HpackEncoder;
import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.FrameUtils;
import packetproxy.model.Packet;

public abstract class FramesBase {

	protected FrameManager clientFrameManager = new FrameManager();
	protected FrameManager serverFrameManager = new FrameManager();
	protected boolean alreadySentClientRequestPrologue = false;
	protected boolean alreadySentClientRequestEpilogue = false;

	public FramesBase() throws Exception {
		clientFrameManager = new FrameManager();
		serverFrameManager = new FrameManager();
	}

	public String getName() {
		return "HTTP2 Frames Base";
	}

	public int checkDelimiter(byte[] data) throws Exception {
		return FrameUtils.checkDelimiter(data);
	}

	public void clientRequestArrived(byte[] frames) throws Exception {
		clientFrameManager.write(frames);
	}

	public void serverResponseArrived(byte[] frames) throws Exception {
		serverFrameManager.write(frames);
	}

	public byte[] passThroughClientRequest() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (alreadySentClientRequestPrologue == false) {

			out.write(FrameUtils.PREFACE);
			out.write(FrameUtils.SETTINGS);
			out.write(FrameUtils.WINDOW_UPDATE);
			alreadySentClientRequestPrologue = true;
		}
		for (Frame frame : clientFrameManager.readControlFrames()) {

			out.write(frame.toByteArray());
		}
		return out.toByteArray();
	}

	public byte[] passThroughServerResponse() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (alreadySentClientRequestEpilogue == false) {

			out.write(FrameUtils.SETTINGS);
			out.write(FrameUtils.WINDOW_UPDATE);
			alreadySentClientRequestEpilogue = true;
		}
		for (Frame frame : serverFrameManager.readControlFrames()) {

			out.write(frame.toByteArray());
		}
		return out.toByteArray();
	}

	public byte[] clientRequestAvailable() throws Exception {
		List<Frame> frames = clientFrameManager.readHeadersDataFrames();
		// frames.stream().forEach(frame -> Logging.log("--> * " + frame));
		return passFramesToDecodeClientRequest(frames);
	}

	public byte[] serverResponseAvailable() throws Exception {
		List<Frame> frames = serverFrameManager.readHeadersDataFrames();
		// frames.stream().forEach(frame -> Logging.log(" * <-- " + frame));
		return passFramesToDecodeServerResponse(frames);
	}

	public byte[] decodeClientRequest(byte[] frames) throws Exception {
		return decodeClientRequestFromFrames(frames);
	}

	public byte[] encodeClientRequest(byte[] data) throws Exception {
		byte[] frames = encodeClientRequestToFrames(data);
		// FrameUtils.parseFrames(frames).stream().forEach(frame -> Logging.log("
		// * --> " + frame));
		return frames;
	}

	public byte[] decodeServerResponse(byte[] frames) throws Exception {
		return decodeServerResponseFromFrames(frames);
	}

	public byte[] encodeServerResponse(byte[] data) throws Exception {
		byte[] frames = encodeServerResponseToFrames(data);
		// FrameUtils.parseFrames(frames).stream().forEach(frame ->
		// Logging.log("<-- * " + frame));
		return frames;
	}

	public void putToClientFlowControlledQueue(byte[] frames) throws Exception {
		clientFrameManager.putToFlowControlledQueue(frames);
	}

	public void putToServerFlowControlledQueue(byte[] frames) throws Exception {
		serverFrameManager.putToFlowControlledQueue(frames);
	}

	public void closeClientFlowControlledQueue() throws Exception {
		clientFrameManager.closeFlowControlledQueue();
	}

	public void closeServerFlowControlledQueue() throws Exception {
		serverFrameManager.closeFlowControlledQueue();
	}

	public InputStream getClientFlowControlledInputStream() {
		return clientFrameManager.getFlowControlledInputStream();
	}

	public InputStream getServerFlowControlledInputStream() {
		return serverFrameManager.getFlowControlledInputStream();
	}

	protected HpackDecoder getClientHpackDecoder() {
		return clientFrameManager.getHpackDecoder();
	}

	protected HpackEncoder getClientHpackEncoder() {
		return clientFrameManager.getHpackEncoder();
	}

	protected HpackDecoder getServerHpackDecoder() {
		return serverFrameManager.getHpackDecoder();
	}

	protected HpackEncoder getServerHpackEncoder() {
		return serverFrameManager.getHpackEncoder();
	}

	protected abstract byte[] passFramesToDecodeClientRequest(List<Frame> frames) throws Exception;

	protected abstract byte[] passFramesToDecodeServerResponse(List<Frame> frames) throws Exception;

	protected abstract byte[] decodeClientRequestFromFrames(byte[] frames) throws Exception;

	protected abstract byte[] decodeServerResponseFromFrames(byte[] frames) throws Exception;

	protected abstract byte[] encodeClientRequestToFrames(byte[] data) throws Exception;

	protected abstract byte[] encodeServerResponseToFrames(byte[] data) throws Exception;

	public abstract void setGroupId(Packet packet) throws Exception;
}
