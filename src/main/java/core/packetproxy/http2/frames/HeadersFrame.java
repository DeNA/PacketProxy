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
    static protected byte FLAG_END_STREAM = 0x01;
    static protected byte FLAG_END_HEADERS = 0x04;
    static protected byte FLAG_PADDED = 0x08;
    static protected byte FLAG_PRIORITY = 0x20;
	
	private String method;
	private String path;
	private String scheme;
	private String authority;
	private String query;
	private String fragment;
	private int port;
	private String uriString;
	private HttpFields fields;
	private boolean bRequest;
	private int status;
	private HttpVersion version;
	
	public HeadersFrame(Frame frame, HpackDecoder decoder) throws Exception {
		super(frame);
		parsePayload(decoder);
	}

	public HeadersFrame(byte[] frameData, HpackDecoder decoder) throws Exception {
		super(frameData);
		parsePayload(decoder);
	}
	
	public HeadersFrame(Http http, HpackEncoder encoder) throws Exception {
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
				HttpURI uri = new HttpURI(uriString);
				scheme = uri.getScheme();
				authority = uri.getAuthority();
				path = uri.getPath();
				port = uri.getPort();
				query = uri.getQuery();
				fragment = uri.getFragment();
			} else {
				fields.add(field.getName(), field.getValue());
			}
		}
		
		MetaData meta;
		if (http.isRequest()) {
			long contentLength = 0;
			if (method.equals("GET")) {
				contentLength = (http.getBody().length == 0 ? Long.MIN_VALUE : http.getBody().length);
			} else if (method.equals("POST")) {
				contentLength = http.getBody().length;
				fields.add("content-length", String.valueOf(contentLength));
			}
			HttpURI uri = HttpURI.createHttpURI(scheme, authority, port, path, null, query, fragment);
			meta = new MetaData.Request(method, uri, version, fields, contentLength);
		} else {
			long contentLength = (http.getBody().length == 0 ? Long.MIN_VALUE : http.getBody().length);
			meta = new MetaData.Response(version, Integer.parseInt(http.getStatusCode()), fields, contentLength);
		}

		ByteBuffer buffer = ByteBuffer.allocate(65535);
		encoder.encode(buffer, meta);
		
		byte[] array = new byte[buffer.position()];
		buffer.flip();
		buffer.get(array);
		payload = array;

		if (http.getBody().length > 0)
			super.flags = FLAG_END_HEADERS;
		else
			super.flags = FLAG_END_HEADERS | FLAG_END_STREAM;

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
	
	private void parsePayload(HpackDecoder decoder) throws Exception {
    	ByteBuffer in = ByteBuffer.allocate(65535);
    	in.put(payload);
    	in.flip();
    	
    	if ((flags & FLAG_PRIORITY) > 0) {
    		in.getInt(); // skip dependency
    		in.get(); // skip weight
    	}
    	
    	MetaData meta = decoder.decode(in);

    	if (meta instanceof Request) {
    		//System.out.println("# meta.request: " + meta);
    		bRequest = true;
    		Request req = (Request)meta;
    		method = req.getMethod();
    		version = req.getHttpVersion();
    		uriString = req.getURIString();
    		HttpURI uri = req.getURI();
    		scheme = uri.getScheme();
    		authority = uri.getAuthority();
    		path = uri.getPath();
    		query = uri.getQuery();
    		fragment = uri.getFragment();

    	} else {
    		//System.out.println("# meta.response: " + meta);
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
			String queryStr    = (query != null && query.length() > 0) ? "?"+query : "";
			String fragmentStr = (fragment != null && fragment.length() > 0) ? "#"+fragment : "";
			String statusLine = String.format("%s %s%s%s HTTP/2.0\r\n", method, path, queryStr, fragmentStr);
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
		String msg;
		if (isRequest()) {
			msg = String.format("method:%s, scheme:%s, authority:%s, path:%s\n", method, scheme, authority, path);
		} else {
			msg = String.format("status:%d\n", status);
		}
		return super.toString() + "\n" + msg + fields.toString();
	}

}
