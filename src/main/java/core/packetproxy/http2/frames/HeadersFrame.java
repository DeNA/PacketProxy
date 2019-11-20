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
package packetproxy.http2.frames;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.MetaData.Request;
import org.eclipse.jetty.http.MetaData.Response;
import org.eclipse.jetty.http2.hpack.HpackDecoder;

public class HeadersFrame extends Frame {

    static protected Type TYPE = Type.HEADERS; 
	
	private String method;
	private String path;
	private String scheme;
	private String authority;
	private HttpFields fields;
	private boolean bRequest;
	private int status;
	
	//public static void main(String[] args) throws Exception {
    //	byte[] frame = Hex.decodeHex("00001B010500000001828487418798E79A82AE43D37A8825B650C3ABB6D2E053032A2F2A".toCharArray());
   // 	HeadersFrame fb = new HeadersFrame(frame);
   // 	System.out.println(fb.toString());
	//}
	
	public HeadersFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
	}

	public HeadersFrame(byte[] data) throws Exception {
		super(data);
		parsePayload();
	}
	
	public String getMethod() { return method; }
	public String getPath() { return path; }
	public String getScheme() { return scheme; }
	public String getAuthority() { return authority; }
	public HttpFields getHttpFields() { return fields; }
	public boolean isRequest() { return bRequest; }
	public int getStatus() { return status; }
	
	private void parsePayload() throws Exception {
    	ByteBuffer in = ByteBuffer.allocate(4096);
    	in.put(payload);
    	in.flip();
    	
    	HpackDecoder decoder = new HpackDecoder(4096, 4096);
    	MetaData meta = decoder.decode(in);

    	if (meta instanceof Request) {
    			bRequest = true;
    			Request req = (Request)meta;
    			method = req.getMethod();
    			URI uri = new URI(req.getURIString());
    			scheme = uri.getScheme();
    			authority = uri.getAuthority();
    			path = uri.getPath();
    	} else {
    			bRequest = false;
    			Response res = (Response)meta;
    			status = res.getStatus();
    	}
    	fields = meta.getFields();
	}
	
	@Override
	public byte[] toHttp1() throws Exception {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		if (bRequest) {
			String statusLine = String.format("%s %s://%s%s HTTP/2.0\r\n", method, scheme, authority, path);
			buf.write(statusLine.getBytes());
		} else {
			buf.write(String.format("HTTP/2.0 %d %s\r\n", status, HttpStatus.getMessage(status)).getBytes());
		}
		for (HttpField field: fields) {
			buf.write(String.format("%s: %s\r\n", field.getName(), field.getValue()).getBytes());
		}
		buf.write("\r\n\r\n".getBytes());
		return buf.toByteArray();
	}
	
	@Override
	public String toString() {
		return super.toString() + "\n" +
				String.format("method:%s, scheme:%s, authority:%s, path:%s", method, scheme, authority, path) + "\n" +
				fields.toString();
	}

}
