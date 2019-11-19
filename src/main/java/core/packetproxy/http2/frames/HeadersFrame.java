package packetproxy.http2.frames;

import java.net.URI;
import java.nio.ByteBuffer;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.MetaData.Request;
import org.eclipse.jetty.http2.hpack.HpackDecoder;

public class HeadersFrame extends Frame {
	
	private String method;
	private String path;
	private String scheme;
	private String authority;
	private HttpFields fields;
	
	public static void main(String[] args) throws Exception {

    	byte[] frame = Hex.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
    	HeadersFrame fb = new HeadersFrame(frame);
    	System.out.println(fb.toString());
	}

	public HeadersFrame(byte[] data) throws Exception {
		super(data);

    	ByteBuffer in = ByteBuffer.allocate(4096);
    	in.put(payload);
    	in.flip();
    	
    	HpackDecoder decoder = new HpackDecoder(4096, 4096);
    	MetaData meta = decoder.decode(in);

    	if (meta instanceof Request) {
    		Request req = (Request)meta;
    		method = req.getMethod();
    		URI uri = new URI(req.getURIString());
    		scheme = uri.getScheme();
    		authority = uri.getAuthority();
    		path = uri.getPath();
    	}
    	fields = meta.getFields();
	}
	
	@Override
	public String toString() {
		return super.toString() + "\n" +
				String.format("method:%s, scheme:%s, authority:%s, path:%s", method, scheme, authority, path) + "\n" +
				fields.toString();
	}

}
