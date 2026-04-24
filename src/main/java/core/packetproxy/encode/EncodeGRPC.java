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
import packetproxy.grpc.GrpcProtoWireFormat;
import packetproxy.grpc.GrpcServiceRegistry;
import packetproxy.grpc.GrpcServiceRegistryStore;
import packetproxy.http.Http;
import packetproxy.http2.Grpc;

public class EncodeGRPC extends EncodeHTTPBase {

	private volatile GrpcServiceRegistry registry;
	private volatile String lastGrpcPath;

	public synchronized void setDescriptorFile(File descFile) {
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

	public EncodeGRPC() throws Exception {
		super();
	}

	public EncodeGRPC(String ALPN) throws Exception {
		super(ALPN, new Grpc());
	}

	@Override
	public String getName() {
		return "gRPC";
	}

	@Override
	protected Http decodeClientRequestHttp(Http inputHttp) throws Exception {
		lastGrpcPath = inputHttp.getPath();
		byte[] raw = inputHttp.getBody();
		inputHttp.setBody(GrpcProtoWireFormat.decodeGrpcHttpBodyToUtf8(raw, registry, true, lastGrpcPath, null));
		return inputHttp;
	}

	@Override
	protected Http encodeClientRequestHttp(Http inputHttp) throws Exception {
		lastGrpcPath = inputHttp.getPath();
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
		inputHttp.setBody(GrpcProtoWireFormat.decodeGrpcHttpBodyToUtf8(raw, registry, false, null, lastGrpcPath));
		return inputHttp;
	}

	@Override
	protected Http encodeServerResponseHttp(Http inputHttp) throws Exception {
		byte[] body = inputHttp.getBody();
		if (body.length == 0) {
			return inputHttp;
		}
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
