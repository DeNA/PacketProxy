package packetproxy.encode;

import net.arnx.jsonic.JSON;
import packetproxy.common.GRPCMessage;
import packetproxy.http.Http;

import java.util.List;
import java.util.Map;

public class EncodeGRPCWeb extends EncodeHTTPBase
{
	public EncodeGRPCWeb(String ALPN) throws Exception {
		super(ALPN);
	}

    @Override
    protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text+proto")) {
            String base64Body = new String(inputHttp.getBody());
            inputHttp.setBody(JSON.encode(GRPCMessage.decodeMessages(base64Body)).getBytes());
        }
        return inputHttp;
    }

    @Override
    protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text+proto")) {
            List<Map<String, Object>> json = JSON.decode(new String(inputHttp.getBody()));
            inputHttp.setBody(GRPCMessage.encodeMessages(json).getBytes());
        }
        return inputHttp;
    }

    @Override
    protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text")) {
            String base64Body = new String(inputHttp.getBody());
            inputHttp.setBody(JSON.encode(GRPCMessage.decodeMessages(base64Body)).getBytes());
        }
        return inputHttp;
    }

    @Override
    protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
        if (inputHttp.getFirstHeader("Content-Type").equals("application/grpc-web-text")) {
            List<Map<String, Object>> json = JSON.decode(new String(inputHttp.getBody()));
            inputHttp.setBody(GRPCMessage.encodeMessages(json).getBytes());
        }
        return inputHttp;
    }

    @Override
    public String getName() {
        return "gRPC-Web";
    }
}
