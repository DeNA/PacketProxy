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
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.http2.hpack.HpackDecoder;
import org.eclipse.jetty.http2.hpack.HpackEncoder;
import packetproxy.http2.frames.*;
import packetproxy.http2.frames.SettingsFrame.SettingsFrameType;

public class FrameManager {

	private HpackEncoder hpackEncoder = new HpackEncoder(4096, 65536);
	private HpackDecoder hpackDecoder;
	private List<Frame> headersDataFrames = new LinkedList<>();
	private List<Frame> controlFrames = new LinkedList<>();
	private FlowControlManager flowControlManager;
	private boolean flag_receive_peer_settings = false;
	private boolean flag_send_settings = false;
	private boolean flag_send_end_settings = false;

	public FrameManager() throws Exception {
		flowControlManager = new FlowControlManager();
	}

	public HpackDecoder getHpackDecoder() {
		return hpackDecoder;
	}

	public HpackEncoder getHpackEncoder() {
		return hpackEncoder;
	}

	public FlowControlManager getFlowControlManager() {
		return flowControlManager;
	}

	public void write(List<Frame> frames) throws Exception {
		for (Frame frame : frames) {

			analyzeFrame(frame);
		}
	}

	public void write(byte[] frames) throws Exception {
		for (Frame frame : FrameUtils.parseFrames(frames, hpackDecoder)) {

			analyzeFrame(frame);
		}
	}

	private void analyzeFrame(Frame frame) throws Exception {
		if (frame instanceof HeadersFrame) {

			HeadersFrame headersFrame = (HeadersFrame) frame;
			headersDataFrames.add(headersFrame);
			// Logging.log("HeadersFrame: " + headersFrame);
		} else if (frame instanceof DataFrame) {

			DataFrame dataFrame = (DataFrame) frame;
			headersDataFrames.add(dataFrame);
			// Logging.log("DataFrame: " + dataFrame);
		} else if (frame instanceof SettingsFrame) {

			SettingsFrame settingsFrame = (SettingsFrame) frame;
			flowControlManager.setInitialWindowSize(settingsFrame);
			if ((settingsFrame.getFlags() & 0x1) == 0) {

				int header_table_size = settingsFrame.get(SettingsFrameType.SETTINGS_HEADER_TABLE_SIZE);
				int header_list_size = settingsFrame.get(SettingsFrameType.SETTINGS_MAX_HEADER_LIST_SIZE);
				hpackDecoder = new HpackDecoder(header_table_size, header_list_size);
				flag_receive_peer_settings = true;
				if (flag_send_end_settings == false && flag_send_settings == true) {

					flowControlManager.getOutputStream().write(FrameUtils.END_SETTINGS);
					flowControlManager.getOutputStream().flush();
					flag_send_end_settings = true;
				}
			}
			// Logging.log("SettingsFrame: " + settingsFrame);
			// System.out.flush();
		} else if (frame instanceof GoawayFrame) {

			GoawayFrame goAwayFrame = (GoawayFrame) frame;
			if (goAwayFrame.getErrorCode() != 0) { // 0 is NO_ERROR

				err("GoAway:%s", goAwayFrame);
			}
		} else if (frame instanceof RstStreamFrame) {

			RstStreamFrame rstFrame = (RstStreamFrame) frame;
			if (rstFrame.getErrorCode() != 0 && rstFrame.getErrorCode() != 8) { // 0 is NO_ERROR, 8 is CANCEL

				err("RstStream:%s", rstFrame);
			}
		} else if (frame instanceof WindowUpdateFrame) {

			WindowUpdateFrame windowUpdateFrame = (WindowUpdateFrame) frame;
			flowControlManager.appendWindowSize(windowUpdateFrame);
		} else if (frame instanceof PingFrame) {

			PingFrame pingFrame = (PingFrame) frame;
			controlFrames.add(pingFrame);
			// Logging.log("Ping:" + pingFrame);
			// System.out.flush();
		} else {

			controlFrames.add(frame);
		}
	}

	public List<Frame> readControlFrames() throws Exception {
		List<Frame> out = new LinkedList<>();
		for (Frame frame : controlFrames) {

			out.add(frame);
		}
		controlFrames.clear();
		return out;
	}

	public List<Frame> readHeadersDataFrames() throws Exception {
		List<Frame> out = new LinkedList<>();
		for (Frame frame : headersDataFrames) {

			out.add(frame);
		}
		headersDataFrames.clear();
		return out;
	}

	ByteArrayOutputStream baos = new ByteArrayOutputStream();

	public void putToFlowControlledQueue(byte[] frameData) throws Exception {
		baos.write(frameData);
		baos.flush();
		int length = 0;
		while ((length = FrameUtils.checkDelimiter(baos.toByteArray())) > 0) {

			byte[] frame = ArrayUtils.subarray(baos.toByteArray(), 0, length);
			byte[] remaining = ArrayUtils.subarray(baos.toByteArray(), length, baos.size());
			baos.reset();
			baos.write(remaining);
			baos.flush();
			if (FrameUtils.isPreface(frame)) {

				flowControlManager.getOutputStream().write(frame);
				flowControlManager.getOutputStream().flush();
			} else {

				Frame f = new Frame(frame);
				flowControlManager.write(f);
				if (f.getType() == Frame.Type.SETTINGS) {

					flag_send_settings = true;
					if (flag_send_end_settings == false && flag_receive_peer_settings == true) {

						flowControlManager.getOutputStream().write(FrameUtils.END_SETTINGS);
						flowControlManager.getOutputStream().flush();
						flag_send_end_settings = true;
					}
				}
			}
		}
	}

	public void closeFlowControlledQueue() throws Exception {
		flowControlManager.getOutputStream().close();
	}

	public InputStream getFlowControlledInputStream() {
		return flowControlManager.getInputStream();
	}
}
