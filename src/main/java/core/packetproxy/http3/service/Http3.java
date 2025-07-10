/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy.http3.service;

import static packetproxy.util.Throwing.rethrow;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jetty.http.*;
import packetproxy.common.UniqueID;
import packetproxy.http.HeaderField;
import packetproxy.http.Http;
import packetproxy.http.HttpHeader;
import packetproxy.http3.value.Setting;
import packetproxy.model.Packet;
import packetproxy.quic.utils.Constants;
import packetproxy.quic.value.QuicMessages;
import packetproxy.quic.value.StreamId;

public class Http3 {

	final StreamsReader clientStreamsReader = new StreamsReader(Constants.Role.CLIENT);
	final StreamsWriter clientStreamsWriter = new StreamsWriter(Constants.Role.CLIENT);
	final StreamsReader serverStreamsReader = new StreamsReader(Constants.Role.SERVER);
	final StreamsWriter serverStreamsWriter = new StreamsWriter(Constants.Role.SERVER);
	final Http3HeaderDecoder clientDecoder = new Http3HeaderDecoder();
	final Http3HeaderDecoder serverDecoder = new Http3HeaderDecoder();
	final Http3HeaderEncoder serverEncoder = new Http3HeaderEncoder(0);
	Http3HeaderEncoder clientEncoder = null;
	private Setting serverSetting = null;

	public Http3() {
		this.serverStreamsWriter.writeSetting(Setting.generateWithDefaultValue());
		this.clientStreamsWriter.writeSetting(Setting.generateWithDefaultValue());
	}

	public int checkDelimiter(byte[] input) {
		if (input == null || input.length < 16) {

			return -1;
		}
		ByteBuffer buffer = ByteBuffer.wrap(input);
		long streamId = buffer.getLong();
		long streamDataSize = buffer.getLong();
		int quicMsgSize = (int) (8 + 8 + streamDataSize);
		return quicMsgSize <= input.length ? quicMsgSize : -1;
	}

	public void clientRequestArrived(byte[] input) throws Exception {
		QuicMessages msgs = QuicMessages.parse(input);
		this.clientStreamsReader.write(msgs);
	}

	public byte[] passThroughClientRequest() throws Exception {
		if (this.clientEncoder == null) {

			this.clientStreamsReader.getSetting().ifPresent(setting -> {

				this.clientEncoder = new Http3HeaderEncoder(setting.getQpackMaxTableCapacity());
			});
		}

		byte[] encode = this.clientStreamsReader.readQpackEncodeData();
		this.clientDecoder.putInstructions(encode);

		if (this.clientEncoder != null) {

			byte[] decode = this.clientStreamsReader.readQpackDecodeData();
			this.clientEncoder.putInstructions(decode);
		}

		QuicMessages msgs = this.serverStreamsWriter.readQuickMessages();
		return msgs.getBytes();
	}

	public byte[] generateReqHttp(HttpRaw httpRaw) throws Exception {
		ByteArrayOutputStream http = new ByteArrayOutputStream();
		this.clientDecoder.decode(httpRaw.getStreamId().getId(), httpRaw.getEncodedHeader())
				.forEach(rethrow(metaData -> {

					MetaData.Request req = (MetaData.Request) metaData;
					String method = req.getMethod();
					HttpURI uri = req.getURI();
					String authority = uri.getAuthority();
					String path = uri.getPath();
					String query = uri.getQuery();
					String queryStr = (query != null && query.length() > 0) ? "?" + query : "";

					http.write(String.format("%s %s%s HTTP/3\r\n", method, path, queryStr).getBytes());
					req.getFields().forEach(rethrow(field -> {

						http.write(String.format("%s: %s\r\n", field.getName(), field.getValue()).getBytes());
					}));
					http.write(String.format("x-packetproxy-http3-host: %s\r\n", authority).getBytes());
				}));
		http.write(String.format("x-packetproxy-http3-stream-id: %d\r\n", httpRaw.getStreamId().getId()).getBytes());
		http.write("\r\n".getBytes());
		this.clientStreamsWriter.writeQpackDecodeData(this.clientDecoder.getInstructions());
		http.write(httpRaw.getBody());
		return http.toByteArray();
	}

	public byte[] generateResHttp(HttpRaw httpRaw) throws Exception {
		ByteArrayOutputStream http = new ByteArrayOutputStream();
		this.serverDecoder.decode(httpRaw.getStreamId().getId(), httpRaw.getEncodedHeader())
				.forEach(rethrow(metaData -> {

					MetaData.Response res = (MetaData.Response) metaData;
					http.write(
							String.format("HTTP/3 %d %s\r\n", res.getStatus(), HttpStatus.getMessage(res.getStatus()))
									.getBytes());
					res.getFields().forEach(rethrow(field -> {

						http.write(String.format("%s: %s\r\n", field.getName(), field.getValue()).getBytes());
					}));
				}));
		http.write(String.format("x-packetproxy-http3-stream-id: %d\r\n", httpRaw.getStreamId().getId()).getBytes());
		http.write("\r\n".getBytes());
		this.serverStreamsWriter.writeQpackDecodeData(this.serverDecoder.getInstructions());
		http.write(httpRaw.getBody());
		return http.toByteArray();
	}

	public HttpRaw generateHttpRaw(byte[] httpBytes) throws Exception {
		return generateHttpRaw(Http.create(httpBytes));
	}

	public HttpRaw generateHttpRaw(Http http) throws Exception {
		String method = http.getMethod();
		String uriString = "";
		StreamId streamId = null;
		HttpVersion version = HttpVersion.fromString("HTTP/3.0");

		HttpHeader headers = http.getHeader();
		HttpFields.Mutable mutableFields = HttpFields.build();
		for (HeaderField field : headers.getFields()) {

			if (field.getName().equals("x-packetproxy-http3-host")) {

				String scheme = "https";
				String authority = field.getValue();
				String path = http.getPath();
				String query = http.getQueryAsString();
				String queryStr = (query != null && query.length() > 0) ? "?" + query : "";
				uriString = scheme + "://" + authority + path + queryStr;

			} else if (field.getName().equals("x-packetproxy-http3-stream-id")) {

				streamId = StreamId.of(Long.parseLong(field.getValue()));
			} else {

				mutableFields.add(field.getName(), field.getValue());
			}
		}
		HttpFields fields = mutableFields;

		MetaData meta;
		if (http.isRequest()) {

			long contentLength = 0;
			if (method.equals("GET") || method.equals("HEAD")) {

				contentLength = (http.getBody().length == 0 ? Long.MIN_VALUE : http.getBody().length);
			} else if (method.equals("POST") || method.equals("PUT")) {

				contentLength = http.getBody().length;
				mutableFields.add("content-length", String.valueOf(contentLength));
				fields = mutableFields;
			}
			HttpURI uri = HttpURI.build().uri(uriString);
			meta = new MetaData.Request(method, uri, version, fields, contentLength);
		} else {

			long contentLength = (http.getBody().length == 0 ? Long.MIN_VALUE : http.getBody().length);
			meta = new MetaData.Response(version, Integer.parseInt(http.getStatusCode()), fields, contentLength);
		}

		byte[] encodedHeader;
		if (http.isRequest()) {

			encodedHeader = this.serverEncoder.encode(streamId.getId(), meta);
			this.serverStreamsWriter.writeQpackEncodeData(this.serverEncoder.getInstructions());
		} else {

			encodedHeader = this.clientEncoder.encode(streamId.getId(), meta);
			this.clientStreamsWriter.writeQpackEncodeData(this.clientEncoder.getInstructions());
		}
		byte[] body = http.getBody();

		return HttpRaw.of(streamId, encodedHeader, body);
	}

	public byte[] clientRequestAvailable() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		this.clientStreamsReader.readHttpRaw().ifPresent(rethrow(httpRaw -> {

			bytes.write(generateReqHttp(httpRaw));
		}));
		return bytes.toByteArray();
	}

	public byte[] decodeClientRequest(byte[] input) throws Exception {
		return input;
	}

	public byte[] encodeClientRequest(byte[] input) throws Exception {
		HttpRaw httpRaw = this.generateHttpRaw(input);
		this.serverStreamsWriter.write(httpRaw);
		return this.serverStreamsWriter.readQuickMessages().getBytes();
	}

	public void serverResponseArrived(byte[] input) throws Exception {
		QuicMessages msgs = QuicMessages.parse(input);
		this.serverStreamsReader.write(msgs);
	}

	public byte[] passThroughServerResponse() throws Exception {
		if (this.serverSetting == null) {

			this.serverStreamsReader.getSetting().ifPresent(setting -> {

				this.serverSetting = setting;
			});
		}
		byte[] encode = this.serverStreamsReader.readQpackEncodeData();
		this.serverDecoder.putInstructions(encode);

		byte[] decode = this.serverStreamsReader.readQpackDecodeData();
		this.serverEncoder.putInstructions(decode);

		QuicMessages msgs = this.clientStreamsWriter.readQuickMessages();
		return msgs.getBytes();
	}

	public byte[] serverResponseAvailable() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		this.serverStreamsReader.readHttpRaw().ifPresent(rethrow(httpRaw -> {

			bytes.write(generateResHttp(httpRaw));
		}));
		return bytes.toByteArray();
	}

	public byte[] decodeServerResponse(byte[] input) throws Exception {
		return input;
	}

	public byte[] encodeServerResponse(byte[] input) throws Exception {
		HttpRaw httpRaw = this.generateHttpRaw(input);
		this.clientStreamsWriter.write(httpRaw);
		return this.clientStreamsWriter.readQuickMessages().getBytes();
	}

	private Map<Long, Long> groupMap = new HashMap<>();

	public void setGroupId(Packet packet) throws Exception {
		byte[] data = (packet.getDecodedData().length > 0) ? packet.getDecodedData() : packet.getModifiedData();
		Http http = Http.create(data);
		String streamIdStr = http.getFirstHeader("x-packetproxy-http3-stream-id");
		if (streamIdStr != null && streamIdStr.length() > 0) {

			long streamId = Long.parseLong(streamIdStr);
			if (groupMap.containsKey(streamId)) {

				packet.setGroup(groupMap.get(streamId));
			} else {

				long groupId = UniqueID.getInstance().createId();
				groupMap.put(streamId, groupId);
				packet.setGroup(groupId);
			}
		}
	}

}
