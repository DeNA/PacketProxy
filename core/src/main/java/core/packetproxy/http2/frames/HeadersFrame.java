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
import org.apache.commons.lang3.ArrayUtils;
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
import packetproxy.common.StringUtils;
import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;

public class HeadersFrame extends Frame {

	protected static Type TYPE = Type.HEADERS;
	public static byte FLAG_END_STREAM = 0x01;
	public static byte FLAG_END_HEADERS = 0x04;
	public static byte FLAG_PADDED = 0x08;
	public static byte FLAG_PRIORITY = 0x20;
	public static byte FLAG_EXTRA = 0x40; /* internal use only */

	private String method;
	private String path;
	private String scheme;
	private String authority;
	private String query;
	private String uriString;
	private HttpFields fields;
	private boolean bRequest = false;
	private boolean bResponse = false;
	private boolean bTrailer = false;
	private boolean isGRPC2ndResponseHeader;
	private int status;
	private HttpVersion version;
	private boolean priority = false;
	private int dependency = 0;
	private int weight = 0;

	public HeadersFrame(byte[] frameData, HpackDecoder decoder) throws Exception {
		super(frameData);
		decodeToHttp(decoder);
	}

	public HeadersFrame(Frame frame, HpackDecoder decoder) throws Exception {
		super(frame);
		decodeToHttp(decoder);
	}

	public HeadersFrame(Http http) throws Exception {
		super();
		super.type = TYPE;
		for (HeaderField field : http.getHeader().getFields()) {

			if (field.getName().equals("X-PacketProxy-HTTP2-Stream-Id")) {

				super.streamId = Integer.parseInt(field.getValue());
			} else if (field.getName().equals("X-PacketProxy-HTTP2-Flags")) {

				super.flags |= (byte) Integer.parseInt(field.getValue());
			} else if (field.getName().equals("X-PacketProxy-HTTP2-GRPC-2nd-Frame-Header")) {

				this.bTrailer = true;
			}
		}
		super.saveExtra(http.toByteArray());
	}

	public byte[] getHttp() throws Exception {
		return super.getExtra();
	}

	// @Override
	// public byte[] toByteArrayWithoutExtra() throws Exception {
	// throw new Exception("[HeadersFrame] use toByteArrayWithoutExtra(HpackEncoder
	// encoder) rather than toByteArrayWithoutExtra().");
	// }

	public byte[] toByteArrayWithoutExtra(HpackEncoder encoder) throws Exception {
		return toByteArrayWithoutExtra(encoder, false);
	}

	public byte[] toByteArrayWithoutExtra(HpackEncoder encoder, boolean originalHttpHeader) throws Exception {
		return toByteArrayWithoutExtra(encoder, originalHttpHeader, true);
	}

	public byte[] toByteArrayWithoutExtra(HpackEncoder encoder, boolean originalHttpHeader, boolean withContentLength)
			throws Exception {
		encodeFromHttp(encoder, originalHttpHeader, withContentLength);
		return super.toByteArrayWithoutExtra();
	}

	private void encodeFromHttp(HpackEncoder encoder, boolean originalHttpHeader, boolean withContentLength)
			throws Exception {
		if ((super.flags & FLAG_EXTRA) == 0) {

			return;
		}
		Http http = Http.create(getExtra());

		method = http.getMethod();
		version = HttpVersion.fromString("HTTP/2");

		HttpHeader headers = (originalHttpHeader == true ? http.getOriginalHeader() : http.getHeader());
		HttpFields.Mutable mutableFields = HttpFields.build();
		for (HeaderField field : headers.getFields()) {
			if (field.getName().equals("X-PacketProxy-HTTP2-Scheme")) {
				scheme = field.getValue();
			} else if (field.getName().equals("X-PacketProxy-HTTP2-Host")) {
				authority = field.getValue();
				path = http.getPath();
				query = http.getQueryAsString();
				String queryStr = (query != null && query.length() > 0) ? "?" + query : "";
				uriString = scheme + "://" + authority + path + queryStr;
			} else if (field.getName().equals("X-PacketProxy-HTTP2-Dependency")) {

				priority = true;
				dependency = Integer.parseInt(field.getValue());
			} else if (field.getName().equals("X-PacketProxy-HTTP2-Weight")) {

				weight = Integer.parseInt(field.getValue());
			} else if (!withContentLength && field.getName().equals("content-length")) {

				// ignore
			} else if (!field.getName().startsWith("X-PacketProxy")) {

				mutableFields.add(field.getName(), field.getValue());
			}
		}
		fields = mutableFields;

		MetaData meta;
		if (http.isRequest()) {

			long contentLength = 0;
			HttpURI uri = HttpURI.build().uri(uriString);
			if (withContentLength) {

				if (method.equals("GET") || method.equals("HEAD")) {

					contentLength = (http.getBody().length == 0 ? Long.MIN_VALUE : http.getBody().length);
				} else if (method.equals("POST") || method.equals("PUT")) {

					contentLength = http.getBody().length;
					mutableFields.add("content-length", String.valueOf(contentLength));
					fields = mutableFields;
				}
				meta = new MetaData.Request(method, uri, version, fields, contentLength);
			} else {

				meta = new MetaData.Request(method, uri, version, fields);
			}
		} else {

			if (this.bTrailer) {

				meta = new MetaData(version, fields);
			} else {

				this.status = Integer.valueOf(http.getStatusCode());
				long contentLength = (http.getBody().length == 0 ? Long.MIN_VALUE : http.getBody().length);
				meta = new MetaData.Response(version, Integer.parseInt(http.getStatusCode()), fields, contentLength);
			}
		}

		ByteBuffer buffer = ByteBuffer.allocate(65535);
		encoder.encode(buffer, meta);

		byte[] headersPayload = new byte[buffer.position()];
		buffer.flip();
		buffer.get(headersPayload);

		if (priority) {

			ByteBuffer b = ByteBuffer.allocate(16);
			b.putInt(dependency | 0x80000000);
			b.put((byte) (weight & 0xff));
			byte[] priorityField = new byte[b.position()];
			b.flip();
			b.get(priorityField);
			headersPayload = ArrayUtils.addAll(priorityField, headersPayload);
		}

		saveOrigPayload(headersPayload);
	}

	private void decodeToHttp(HpackDecoder decoder) throws Exception {
		if ((super.flags & FLAG_EXTRA) > 0) {

			return;
		}
		if (decoder == null) {

			return;
		}
		ByteBuffer in = ByteBuffer.allocate(65535);
		in.put(super.payload);
		in.flip();

		if ((super.flags & FLAG_PRIORITY) > 0) {

			priority = true;
			dependency = in.getInt() & 0x7fffffff;
			weight = in.get();
		}

		MetaData meta = decoder.decode(in);

		isGRPC2ndResponseHeader = false;

		if (meta instanceof Request) {

			// Logging.log("# meta.request: " + meta);
			bRequest = true;
			Request req = (Request) meta;
			method = req.getMethod();
			version = req.getHttpVersion();
			uriString = req.getURIString();
			HttpURI uri = req.getURI();
			scheme = uri.getScheme();
			authority = uri.getAuthority();
			path = uri.getPath();
			query = uri.getQuery();

		} else if (meta instanceof Response) {

			// Logging.log("# meta.response: " + meta);
			bResponse = true;
			Response res = (Response) meta;
			status = res.getStatus();
		} else {

			// gRPC Trailer Frame
			bTrailer = true;
		}
		fields = meta.getFields();

		if (bTrailer) {

			for (HttpField i : fields) {

				if (i.getName().contains("grpc-status")) {

					isGRPC2ndResponseHeader = true;
					break;
				}
			}
		}

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		if (bRequest) {

			String queryStr = (query != null && query.length() > 0) ? "?" + query : "";
			// String fragmentStr = (fragment != null && fragment.length() > 0) ?
			// "#"+fragment : "";
			String statusLine = String.format("%s %s%s HTTP/2\r\n", method, path, queryStr);
			buf.write(statusLine.getBytes());
		} else {

			buf.write(String.format("HTTP/2 %d %s\r\n", status, HttpStatus.getMessage(status)).getBytes());
		}
		for (HttpField field : fields) {

			buf.write(String.format("%s: %s\r\n", field.getName(), field.getValue()).getBytes());
		}
		if (!isGRPC2ndResponseHeader) {

			if (bRequest) {
				buf.write(String.format("X-PacketProxy-HTTP2-Scheme: %s\r\n", scheme).getBytes());
				buf.write(String.format("X-PacketProxy-HTTP2-Host: %s\r\n", authority).getBytes());
			}
			if (priority) {

				buf.write(String.format("X-PacketProxy-HTTP2-Dependency: %d\r\n", dependency).getBytes());
				buf.write(String.format("X-PacketProxy-HTTP2-Weight: %d\r\n", weight & 0xff).getBytes());
			}
			buf.write(String.format("X-PacketProxy-HTTP2-Type: %d\r\n", TYPE.ordinal()).getBytes());
			buf.write(String.format("X-PacketProxy-HTTP2-Stream-Id: %d\r\n", streamId).getBytes());
			buf.write(String.format("X-PacketProxy-HTTP2-Flags: %d\r\n", flags).getBytes());
			buf.write(String.format("X-PacketProxy-HTTP2-UUID: %s\r\n", StringUtils.randomUUID()).getBytes());
		} else {

			buf.write(String.format("X-PacketProxy-HTTP2-GRPC-2nd-Frame-Header: 1\r\n").getBytes());
		}
		buf.write("\r\n".getBytes());

		saveExtra(buf.toByteArray());
	}
}
