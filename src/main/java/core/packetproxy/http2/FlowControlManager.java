package packetproxy.http2;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;

import packetproxy.http2.frames.Frame;
import packetproxy.http2.frames.SettingsFrame;
import packetproxy.http2.frames.SettingsFrame.SettingsFrameType;
import packetproxy.http2.frames.WindowUpdateFrame;

public class FlowControlManager
{
	private Map<Integer,FlowControl> flows;
	private int PIPE_SIZE = 65536;
	private int initialWindowSize = 65536;
	private PipedOutputStream outputForFlowControl;
	private PipedInputStream inputForFlowControl;
	
	public FlowControlManager() throws Exception {
		outputForFlowControl = new PipedOutputStream();
		inputForFlowControl = new PipedInputStream(outputForFlowControl, PIPE_SIZE);
	}
	
	private FlowControl getFlow(int streamId) {
		FlowControl flow = flows.get(streamId);
		if (flow == null) {
			flow = new FlowControl(initialWindowSize);
			flows.put(streamId, flow);
		}
		return flow;
	}
	
	public void setInitialWindowSize(SettingsFrame frame) {
		initialWindowSize = frame.get(SettingsFrameType.SETTINGS_INITIAL_WINDOW_SIZE);
	}

	public void appendWindowSize(WindowUpdateFrame frame) throws Exception {
		int streamId = frame.getStreamId();
		int windowSize = frame.getWindowSize();
		
		if (streamId == 0) {
			for (FlowControl flow : flows.values()) {
				flow.appendWindowSize(windowSize);
				outputForFlowControl.write(flow.dequeue());
			}
		} else {
			FlowControl flow = getFlow(streamId);
			flow.appendWindowSize(windowSize);
			outputForFlowControl.write(flow.dequeue());
		}
	}

	public void write(Frame frame) throws Exception {
		FlowControl flow = getFlow(frame.getStreamId());
		flow.enqueue(frame.toByteArray());
		outputForFlowControl.write(flow.dequeue());
	}

	public InputStream getInputStream() {
		return inputForFlowControl;
	}


}
