package packetproxy.http2;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.Map;

import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.SettingsFrame;
import packetproxy.http2.frames.SettingsFrame.SettingsFrameType;
import packetproxy.http2.frames.WindowUpdateFrame;

public class FlowControlManager
{
	private Map<Integer,FlowControl> flows;
	private int PIPE_SIZE = 65535;
	private int connectionWindowSize = 65535;
	private int initialStreamWindowSize = 65535;
	private PipedOutputStream outputForFlowControl;
	private PipedInputStream inputForFlowControl;
	
	public FlowControlManager() throws Exception {
		flows = new HashMap<>();
		outputForFlowControl = new PipedOutputStream();
		inputForFlowControl = new PipedInputStream(outputForFlowControl, PIPE_SIZE);
	}
	
	private FlowControl getFlow(int streamId) {
		FlowControl flow = flows.get(streamId);
		if (flow == null) {
			flow = new FlowControl(initialStreamWindowSize);
			flows.put(streamId, flow);
		}
		return flow;
	}
	
	private void writeData(FlowControl flow) throws Exception {
		byte[] data = flow.dequeue(connectionWindowSize);
		connectionWindowSize -= data.length;
		outputForFlowControl.write(data);
	}
	
	public void setInitialWindowSize(SettingsFrame frame) {
		int flags = frame.getFlags();
		if ((flags & 0x1) > 0) {
			return;
		}
		if (initialStreamWindowSize != 65535) {
			System.err.println("[Error] Initial window size is reset. We cannot handle it (not implemented yet)");
		}
		initialStreamWindowSize = frame.get(SettingsFrameType.SETTINGS_INITIAL_WINDOW_SIZE);
	}

	public void appendWindowSize(WindowUpdateFrame frame) throws Exception {
		int streamId = frame.getStreamId();
		int windowSize = frame.getWindowSize();
		
		if (streamId == 0) {
			connectionWindowSize += windowSize;
			for (FlowControl flow : flows.values()) {
				writeData(flow);
			}
		} else {
			FlowControl flow = getFlow(streamId);
			flow.appendWindowSize(windowSize);
			writeData(flow);
		}
	}

	public void write(Frame frame) throws Exception {
		if (frame.getType() == Frame.Type.DATA) {
			FlowControl flow = getFlow(frame.getStreamId());
			flow.enqueue(frame.toByteArray());
			writeData(flow);
		} else {
			outputForFlowControl.write(frame.toByteArray());
		}
	}

	public InputStream getInputStream() {
		return inputForFlowControl;
	}


}
