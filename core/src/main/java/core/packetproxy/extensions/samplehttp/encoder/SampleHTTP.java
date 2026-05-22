package packetproxy.extensions.samplehttp.encoder;

import packetproxy.encode.EncodeHTTPBase;
import packetproxy.http.Http;

public class SampleHTTP extends EncodeHTTPBase {

	public SampleHTTP(String ALPN) throws Exception {
		super(ALPN);
	}

	@Override
	public String getName() {
		return "SampleHTTP from extension";
	}

	@Override
	protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		return inputHttp;
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		return inputHttp;
	}
}
