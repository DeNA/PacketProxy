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
package packetproxy.grpc

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import packetproxy.common.Protobuf3
import packetproxy.http.Http
import packetproxy.util.Logging

/**
 * gRPC body の JSON⇔protobuf 変換と [GrpcServiceRegistry] 解決を担う。
 * エンコーダごとに1インスタンス持ち、[lastResolvedRegistry] を通じて リクエスト時に解決した registry をレスポンス処理でも再利用する。
 */
class GrpcSchemaResolver {

  @Volatile private var lastResolvedRegistry: GrpcServiceRegistry? = null

  @Volatile @JvmField internal var registryOverrideForTest: GrpcServiceRegistry? = null

  fun resolveRegistry(http: Http): GrpcServiceRegistry? {
    return try {
      registryOverrideForTest?.let {
        return it
      }
      var authority = http.getFirstHeader("X-PacketProxy-HTTP2-Host")
      if (authority.isEmpty()) {
        authority = http.getFirstHeader("x-packetproxy-http3-host")
      }
      if (authority.isEmpty()) {
        val host = http.host
        if (!host.isNullOrEmpty()) {
          authority = host
        }
      }
      GrpcServiceRegistryStore.getInstance().getByAuthority(authority)
    } catch (e: Exception) {
      Logging.errWithStackTrace(e)
      null
    }
  }

  fun effectiveRegistry(http: Http): GrpcServiceRegistry? {
    val reg = resolveRegistry(http)
    if (reg != null) {
      lastResolvedRegistry = reg
      return reg
    }
    return lastResolvedRegistry
  }

  fun resolveRegistryForRequest(http: Http): GrpcServiceRegistry? {
    val reg = resolveRegistry(http)
    if (reg != null) {
      lastResolvedRegistry = reg
    }
    return reg
  }

  @Throws(Exception::class)
  fun decodeSchemaAwareBody(raw: ByteArray, type: Descriptor): ByteArray {
    val body = ByteArrayOutputStream()
    var pos = 0
    while (pos < raw.size) {
      if (raw[pos] != 0.toByte()) {
        throw Exception("gRPC: compressed flag in gRPC message is not supported yet")
      }
      pos += 1
      val messageLength = ByteBuffer.wrap(raw, pos, 4).int
      pos += 4
      val grpcMsg = raw.copyOfRange(pos, pos + messageLength)
      pos += messageLength
      if (body.size() > 0) {
        body.write('\n'.code)
      }
      val json =
        try {
          JSON_PRINTER.print(DynamicMessage.parseFrom(type, grpcMsg))
        } catch (_: Exception) {
          Protobuf3.decode(grpcMsg)
        }
      body.write(json.toByteArray(StandardCharsets.UTF_8))
    }
    return body.toByteArray()
  }

  @Throws(Exception::class)
  fun encodeSchemaAwareBody(body: ByteArray, type: Descriptor): ByteArray {
    val s = String(body, StandardCharsets.UTF_8)
    var objects = splitTopLevelJsonObjects(s)
    if (objects.isEmpty() && s.trim().isNotEmpty()) {
      objects = listOf(s)
    }
    val rawStream = ByteArrayOutputStream()
    for (json in objects) {
      val trimmed = json.trim()
      if (trimmed.isEmpty()) continue
      val data =
        try {
          val builder = DynamicMessage.newBuilder(type)
          JSON_PARSER.merge(trimmed, builder)
          builder.build().toByteArray()
        } catch (_: Exception) {
          Protobuf3.encode(trimmed)
        }
      rawStream.write(0)
      rawStream.write(ByteBuffer.allocate(4).putInt(data.size).array())
      rawStream.write(data)
    }
    return rawStream.toByteArray()
  }

  private fun splitTopLevelJsonObjects(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    val factory = JsonFactory()
    try {
      factory.createParser(text).use { p ->
        var depth = 0
        var start = -1
        while (p.nextToken() != null) {
          if (p.currentToken() == JsonToken.START_OBJECT) {
            if (depth == 0) {
              start = p.tokenLocation.charOffset.toInt()
            }
            depth++
          } else if (p.currentToken() == JsonToken.END_OBJECT) {
            depth--
            if (depth == 0 && start >= 0) {
              val end = p.currentLocation.charOffset.toInt() + 1
              out.add(text.substring(start, end))
            }
          }
        }
      }
    } catch (_: Exception) {}
    return out
  }

  companion object {
    private val JSON_PRINTER: JsonFormat.Printer =
      JsonFormat.printer().preservingProtoFieldNames().alwaysPrintFieldsWithNoPresence()
    private val JSON_PARSER: JsonFormat.Parser = JsonFormat.parser().ignoringUnknownFields()
  }
}
