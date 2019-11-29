package packetproxy.encode;

import packetproxy.http.Http;
import packetproxy.http2.Http2;
import packetproxy.http2.Http2.Http2Type;
import packetproxy.http2.frames.Frame;

public class EncodeHTTP2Resend extends Encoder
{
	private Http2 h2resend;
	private Http2 h2server;
	
	public EncodeHTTP2Resend() throws Exception {
		h2resend = new Http2(Http2Type.RESEND_CLIENT);
		h2server = new Http2(Http2Type.PROXY_SERVER);
	}
	
	@Override
	public String getName() {
		return "HTTP2 Resend";
	}

	@Override
	public int checkRequestDelimiter(byte[] http) throws Exception {
		return http.length; /* input_data is HTTP data */
	}

	@Override
	public int checkResponseDelimiter(byte[] frames) throws Exception {
		return Http2.parseFrameDelimiter(frames); /* input data is frame data */
	}
	
	@Override
	public byte[] passThroughClientRequest() throws Exception { return h2resend.readControlFrames(); }

	@Override
	public void clientRequestArrived(byte[] frame) throws Exception {
		if (frame[0] != 'P' || frame[1] != 'R') {
			Frame f = new Frame(frame);
			System.out.println("Client:" + f);
		}
		super.clientRequestArrived(frame);
	}

	@Override
	public void serverResponseArrived(byte[] frame) throws Exception {
		Frame f = new Frame(frame);
		System.out.println("Server:" + f);
		h2server.writeFrame(frame);
	}

	@Override
	public byte[] serverResponseAvailable() throws Exception { return h2server.readHttp(); }

	@Override
	public byte[] encodeClientRequest(byte[] http) throws Exception {
		return h2resend.httpToFrames(http);
	}

	@Override
	public byte[] decodeServerResponse(byte[] http) throws Exception {
		return new Http(http).toByteArray();
	}

	@Override
	public int checkDelimiter(byte[] input_data) throws Exception {
		/* This func is never called */
		return 0;
	}

	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		/* This func is never called. */
		return null;
	}

	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		/* This func is never called. */
		return null;
	}

}
