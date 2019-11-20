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
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.MetaData.Request;
import org.eclipse.jetty.http.MetaData.Response;
import org.eclipse.jetty.http2.hpack.HpackDecoder;
import org.eclipse.jetty.http2.hpack.HpackEncoder;

import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;

public class HeadersFrame extends Frame {

    static protected Type TYPE = Type.HEADERS; 
	
	private String method;
	private String path;
	private String scheme;
	private String authority;
	private int port;
	private String uriString;
	private HttpFields fields;
	private boolean bRequest;
	private int status;
	private HttpVersion version;
	
	public HeadersFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
	}

	public HeadersFrame(byte[] frameData) throws Exception {
		super(frameData);
		parsePayload();
	}
	
	public HeadersFrame(Http http) throws Exception {
		super();

		method = http.getMethod();
		version = HttpVersion.fromString("HTTP/2.0");
		
		HttpHeader headers = http.getHeader();
		fields = new HttpFields();
		for (HeaderField field : headers.getFields()) {
			if (field.getName().equals("X-PacketProxy-HTTP2-Stream-Id")) {
				super.streamId = Integer.parseInt(field.getValue());
			} else if (field.getName().equals("X-PacketProxy-HTTP2-URI")) {
				uriString = field.getValue();
				URI uri = new URI(uriString);
				scheme = uri.getScheme();
				authority = uri.getAuthority();
				path = uri.getPath();
				port = uri.getPort();
			} else {
				fields.add(field.getName(), field.getValue());
			}
		}
		
		long contentLength = (http.getBody().length == 0 ? Long.MIN_VALUE : http.getBody().length);
		
		MetaData meta;
		if (http.isRequest()) {
			HttpURI uri = HttpURI.createHttpURI(scheme, authority, port, path, null, null, null);
			meta = new MetaData.Request(method, uri, version, fields, contentLength);
		} else {
			fields.add("content-length", "220"); // test
			meta = new MetaData.Response(version, Integer.parseInt(http.getStatusCode()), fields, 220);
		}

		HpackEncoder encoder = new HpackEncoder(4096, 4096);
		ByteBuffer buffer = ByteBuffer.allocate(4096);
		encoder.encode(buffer, meta);
		
		byte[] array = new byte[buffer.position()];
		buffer.flip();
		buffer.get(array);
		payload = array;

		super.type = TYPE;
		super.length = payload.length;
		
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
    			uriString = req.getURIString();
    			URI uri = new URI(uriString);
    			scheme = uri.getScheme();
    			authority = uri.getAuthority();
    			path = uri.getPath();
    			version = req.getHttpVersion();

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
			String statusLine = String.format("%s / HTTP/2.0\r\n", method, scheme, authority, path);
			buf.write(statusLine.getBytes());
		} else {
			buf.write(String.format("HTTP/2.0 %d %s\r\n", status, HttpStatus.getMessage(status)).getBytes());
		}
		for (HttpField field: fields) {
			buf.write(String.format("%s: %s\r\n", field.getName(), field.getValue()).getBytes());
		}
		if (bRequest) {
			buf.write(String.format("X-PacketProxy-HTTP2-URI: %s\r\n", uriString).getBytes());
		}
		buf.write(String.format("X-PacketProxy-HTTP2-Stream-Id: %d\r\n", streamId).getBytes());
		buf.write("\r\n".getBytes());
		return buf.toByteArray();
	}
	
	@Override
	public String toString() {
		return super.toString() + "\n" +
				String.format("method:%s, scheme:%s, authority:%s, path:%s", method, scheme, authority, path) + "\n" +
				fields.toString();
	}

}
