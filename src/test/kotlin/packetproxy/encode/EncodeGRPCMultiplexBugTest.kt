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
package packetproxy.encode

import com.google.protobuf.DynamicMessage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import packetproxy.grpc.GrpcServiceRegistryStore

/**
 * EncodeGRPC が lastGrpcPath (単一フィールド) でレスポンスのスキーマを選択しているため、 HTTP/2 多重化で複数の unary RPC
 * が同一接続上に来ると、後続リクエストのパスで 先行リクエストのレスポンスをデコードしてしまうバグの再現テスト。
 *
 * multi.desc に含まれるサービス: ServiceA/Call : input=Shared, output=Shared (Shared には "x", "id" フィールド)
 * ServiceB/Ping : input=PingRequest, output=PingResponse (PingResponse には "payload", "latency_ms"
 * フィールド)
 */
class EncodeGRPCMultiplexBugTest {

  private fun resource(classpathPath: String): File {
    val u =
      EncodeGRPCMultiplexBugTest::class.java.getResource(classpathPath)
        ?: throw IllegalStateException("missing resource: $classpathPath")
    return File(u.toURI())
  }

  private fun grpcFrame(protoBytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(0)
    out.write(ByteBuffer.allocate(4).putInt(protoBytes.size).array())
    out.write(protoBytes)
    return out.toByteArray()
  }

  private fun httpRequest(path: String, grpcBody: ByteArray): ByteArray {
    val header =
      "POST $path HTTP/1.1\r\n" +
        "Host: example.com\r\n" +
        "Content-Type: application/grpc\r\n" +
        "Content-Length: ${grpcBody.size}\r\n" +
        "\r\n"
    return header.toByteArray() + grpcBody
  }

  private fun httpResponse(grpcBody: ByteArray): ByteArray {
    val header =
      "HTTP/1.1 200 OK\r\n" +
        "Content-Type: application/grpc\r\n" +
        "Content-Length: ${grpcBody.size}\r\n" +
        "\r\n"
    return header.toByteArray() + grpcBody
  }

  @Test
  fun responseDecodedWithWrongSchema_whenMultipleUnaryRpcsInterleaved() {
    val descFile = resource("/packetproxy/grpc/proto/multidir/multi.desc")
    val registry = GrpcServiceRegistryStore.getInstance().get(descFile)

    val sharedType = registry.getInputType("/pp.multidir.ServiceA/Call")!!
    val pingReqType = registry.getInputType("/pp.multidir.ServiceB/Ping")!!

    val sharedMsg =
      DynamicMessage.newBuilder(sharedType)
        .setField(sharedType.findFieldByName("x"), "hello")
        .setField(sharedType.findFieldByName("id"), 42)
        .build()
    val pingReqMsg =
      DynamicMessage.newBuilder(pingReqType)
        .setField(pingReqType.findFieldByName("ttl"), 100)
        .build()

    val sharedBody = grpcFrame(sharedMsg.toByteArray())
    val pingReqBody = grpcFrame(pingReqMsg.toByteArray())

    // --- baseline: request → response (1 stream, no interleaving) ---
    val baseline = EncodeGRPC()
    baseline.registryOverrideForTest = registry
    baseline.decodeClientRequest(httpRequest("/pp.multidir.ServiceA/Call", sharedBody))
    val baselineResp = String(baseline.decodeServerResponse(httpResponse(sharedBody)))

    // Shared 型でデコードされていれば "x" フィールドが JSON に含まれる
    assertTrue(
      baselineResp.contains("\"x\""),
      "baseline should decode with Shared type containing field 'x'",
    )

    // --- buggy: request1 → request2 → response for request1 ---
    val buggy = EncodeGRPC()
    buggy.registryOverrideForTest = registry
    buggy.decodeClientRequest(httpRequest("/pp.multidir.ServiceA/Call", sharedBody))
    buggy.decodeClientRequest(httpRequest("/pp.multidir.ServiceB/Ping", pingReqBody))
    val buggyResp = String(buggy.decodeServerResponse(httpResponse(sharedBody)))

    // lastGrpcPath が "/pp.multidir.ServiceB/Ping" に上書きされているので
    // PingResponse 型でデコードされ、"x" フィールドは現れない
    assertNotEquals(
      baselineResp,
      buggyResp,
      "BUG REPRODUCED: lastGrpcPath was overwritten by the second request, " +
        "causing the first request's response to be decoded with wrong schema (PingResponse instead of Shared)",
    )
  }
}
