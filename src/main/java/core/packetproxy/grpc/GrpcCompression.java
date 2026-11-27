/*
 * Copyright 2026 DeNA Co., Ltd.
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
package packetproxy.grpc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import packetproxy.http.Http;

/** gRPC の Compressed-Flag 付きメッセージを grpc-encoding ヘッダに従って展開する。 */
public final class GrpcCompression {

	private GrpcCompression() {
	}

	public static String encodingOf(Http http) {
		if (http == null) {
			return null;
		}
		String enc = http.getFirstHeader("grpc-encoding");
		return (enc == null || enc.isEmpty()) ? null : enc.trim().toLowerCase();
	}

	/**
	 * flag が 0 ならそのまま返し、1 なら encoding に従って展開する（encoding が null なら gzip
	 * のマジックバイトがある場合のみ展開）。 展開後は非圧縮 (flag=0) として再エンコードされる。gRPC の仕様上、grpc-encoding
	 * ヘッダがあっても flag=0 のメッセージは許容される。
	 */
	public static byte[] decompress(byte flag, byte[] msg, String encoding) throws Exception {
		if (flag == 0) {
			return msg;
		}
		if (encoding == null) {
			// 仕様上はプロトコル違反だが、プロキシ内でヘッダを取りこぼす場合に備え、gzip のマジックバイトがあるときだけ展開する
			if (isGzip(msg)) {
				return gunzip(msg);
			}
			throw new Exception("gRPC: compressed flag is set but grpc-encoding header is missing");
		}
		switch (encoding) {
			case "gzip" :
				return gunzip(msg);
			case "deflate" :
				return readAll(new InflaterInputStream(new ByteArrayInputStream(msg)));
			case "identity" :
				throw new Exception("gRPC: compressed flag is set but grpc-encoding is identity");
			default :
				throw new Exception("gRPC: unsupported grpc-encoding: " + encoding);
		}
	}

	private static boolean isGzip(byte[] msg) {
		return msg.length >= 2 && (msg[0] & 0xff) == 0x1f && (msg[1] & 0xff) == 0x8b;
	}

	private static byte[] gunzip(byte[] msg) throws Exception {
		return readAll(new GZIPInputStream(new ByteArrayInputStream(msg)));
	}

	private static byte[] readAll(InputStream in) throws Exception {
		try (in) {
			return in.readAllBytes();
		}
	}
}
