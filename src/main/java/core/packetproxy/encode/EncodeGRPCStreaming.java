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
package packetproxy.encode;

import static packetproxy.util.Logging.errWithStackTrace;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import packetproxy.grpc.GrpcProtoWireFormat;
import packetproxy.grpc.GrpcServiceRegistry;
import packetproxy.grpc.GrpcServiceRegistryStore;
import packetproxy.http.Http;
import packetproxy.http2.GrpcStreaming;

// gRPCでデータフレーム1つずつをメッセージと解釈して送受信するエンコーダ
public class EncodeGRPCStreaming extends EncodeHTTPBase {

	private volatile GrpcServiceRegistry registry;
	private volatile String lastGrpcPath;
	private final ConcurrentHashMap<Integer, String> grpcPathByStreamId = new ConcurrentHashMap<>();

	public synchronized void setDescriptorFile(File descFile) {
		grpcPathByStreamId.clear();
		if (descFile == null || !descFile.isFile()) {
			this.registry = null;
			return;
		}
		try {
			this.registry = GrpcServiceRegistryStore.getInstance().get(descFile);
		} catch (Exception e) {
			this.registry = null;
			errWithStackTrace(e);
		}
	}

	private String resolveGrpcPathClient(Http http) {
		String path = http.getPath();
		String streamIdStr = http.getFirstHeader("X-PacketProxy-HTTP2-Stream-Id");
		if (streamIdStr == null || streamIdStr.isEmpty()) {
			return path;
		}
		int streamId;
		try {
			streamId = Integer.parseInt(streamIdStr);
		} catch (NumberFormatException e) {
			return path;
		}
		if ("/trailer-header-frame".equals(path)) {
			String removed = grpcPathByStreamId.remove(streamId);
			return removed != null ? removed : path;
		}
		if ("/data-frame".equals(path)) {
			String mapped = grpcPathByStreamId.get(streamId);
			return mapped != null ? mapped : path;
		}
		grpcPathByStreamId.put(streamId, path);
		return path;
	}

	private String resolveGrpcPathServer(Http http) {
		String path = http.getPath();
		String streamIdStr = http.getFirstHeader("X-PacketProxy-HTTP2-Stream-Id");
		if (streamIdStr == null || streamIdStr.isEmpty()) {
			return path;
		}
		int streamId;
		try {
			streamId = Integer.parseInt(streamIdStr);
		} catch (NumberFormatException e) {
			return path;
		}
		String mapped = grpcPathByStreamId.get(streamId);
		return mapped != null ? mapped : path;
	}

	public EncodeGRPCStreaming() throws Exception {
		super();
	}

	public EncodeGRPCStreaming(String ALPN) throws Exception {
		super(ALPN, new GrpcStreaming());
	}

	@Override
	public String getName() {
		return "gRPC Streaming";
	}

	@Override
	protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
		lastGrpcPath = resolveGrpcPathClient(inputHttp);
		byte[] raw = inputHttp.getBody();
		inputHttp.setBody(GrpcProtoWireFormat.decodeGrpcHttpBodyToUtf8(raw, registry, true, lastGrpcPath, null));
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		lastGrpcPath = resolveGrpcPathClient(inputHttp);
		byte[] body = inputHttp.getBody();
		inputHttp.setBody(GrpcProtoWireFormat.encodeClientRequestHttpBody(body, registry, lastGrpcPath));
		return inputHttp;
	}

	@Override
	protected Http decodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] raw = inputHttp.getBody();
		if (raw.length == 0) {
			return inputHttp;
		}
		lastGrpcPath = resolveGrpcPathServer(inputHttp);
		inputHttp.setBody(GrpcProtoWireFormat.decodeGrpcHttpBodyToUtf8(raw, registry, false, null, lastGrpcPath));
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] body = inputHttp.getBody();
		if (body.length == 0) {
			return inputHttp;
		}
		lastGrpcPath = resolveGrpcPathServer(inputHttp);
		inputHttp.setBody(GrpcProtoWireFormat.encodeServerResponseHttpBody(body, registry, lastGrpcPath));
		return inputHttp;
	}

	public byte[] decodeGrpcClientPayload(byte[] payload) throws Exception {
		return payload;
	}

	public byte[] encodeGrpcClientPayload(byte[] payload) throws Exception {
		return payload;
	}

	public byte[] decodeGrpcServerPayload(byte[] payload) throws Exception {
		return payload;
	}

	public byte[] encodeGrpcServerPayload(byte[] payload) throws Exception {
		return payload;
	}
}
