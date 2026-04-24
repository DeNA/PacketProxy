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

import packetproxy.common.Protobuf3
import packetproxy.common.Utils

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import org.apache.commons.lang3.ArrayUtils

/**
 * gRPC length-prefixed bodies to/from UTF-8 JSON using a [GrpcServiceRegistry], with schema-less
 * fallback.
 */
class GrpcProtoWireFormat private constructor() {
  companion object {
    private val JSON_PRINTER =
      JsonFormat.printer().preservingProtoFieldNames().alwaysPrintFieldsWithNoPresence()

    private val JSON_PARSER = JsonFormat.parser().ignoringUnknownFields()

    @JvmStatic
    @Throws(Exception::class)
    fun decodeGrpcHttpBodyToUtf8(
      raw: ByteArray,
      registry: GrpcServiceRegistry?,
      isRequest: Boolean,
      grpcPath: String?,
      lastRequestGrpcPath: String?,
    ): ByteArray {
      if (registry == null) {
        return decodeSchemalessGrpcBody(raw)
      }
      val type =
        if (isRequest) registry.getInputType(grpcPath)
        else registry.getOutputType(lastRequestGrpcPath)
      if (type == null) {
        return decodeSchemalessGrpcBody(raw)
      }
      val body = ByteArrayOutputStream()
      var pos = 0
      while (pos < raw.size) {
        val compressedFlag = raw[pos]
        if (compressedFlag.toInt() != 0) {
          throw Exception("gRPC: compressed flag in gRPC message is not supported yet")
        }
        pos += 1
        val messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos + 4)).int
        pos += 4
        val grpcMsg = Arrays.copyOfRange(raw, pos, pos + messageLength)
        pos += messageLength
        if (body.size() > 0) {
          body.write('\n'.code)
        }
        body.write(decodeOnePayload(grpcMsg, type).toByteArray(StandardCharsets.UTF_8))
      }
      return body.toByteArray()
    }

    @Throws(Exception::class)
    private fun decodeSchemalessGrpcBody(raw: ByteArray): ByteArray {
      val body = ByteArrayOutputStream()
      var pos = 0
      while (pos < raw.size) {
        val compressedFlag = raw[pos]
        if (compressedFlag.toInt() != 0) {
          throw Exception("gRPC: compressed flag in gRPC message is not supported yet")
        }
        pos += 1
        val messageLength = ByteBuffer.wrap(Arrays.copyOfRange(raw, pos, pos + 4)).int
        pos += 4
        val grpcMsg = Arrays.copyOfRange(raw, pos, pos + messageLength)
        pos += messageLength
        if (body.size() > 0) {
          body.write('\n'.code)
        }
        body.write(Protobuf3.decode(grpcMsg).toByteArray(StandardCharsets.UTF_8))
      }
      return body.toByteArray()
    }

    @Throws(Exception::class)
    private fun decodeOnePayload(payload: ByteArray, type: Descriptor): String {
      return try {
        val msg = DynamicMessage.parseFrom(type, payload)
        JSON_PRINTER.print(msg)
      } catch (_: Exception) {
        Protobuf3.decode(payload)
      }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun encodeClientRequestHttpBody(
      body: ByteArray,
      registry: GrpcServiceRegistry?,
      grpcPath: String?,
    ): ByteArray {
      if (registry == null) {
        return encodeSchemalessGrpcBody(body)
      }
      val type = registry.getInputType(grpcPath) ?: return encodeSchemalessGrpcBody(body)
      return encodeGrpcBodyFromJsonChunks(body, type)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun encodeServerResponseHttpBody(
      body: ByteArray,
      registry: GrpcServiceRegistry?,
      lastRequestGrpcPath: String?,
    ): ByteArray {
      if (body.isEmpty()) {
        return body
      }
      if (registry == null) {
        return encodeSchemalessGrpcBody(body)
      }
      val type =
        registry.getOutputType(lastRequestGrpcPath) ?: return encodeSchemalessGrpcBody(body)
      return encodeGrpcBodyFromJsonChunks(body, type)
    }

    @Throws(Exception::class)
    private fun encodeSchemalessGrpcBody(body: ByteArray): ByteArray {
      if (body.isEmpty()) {
        return body
      }
      val rawStream = ByteArrayOutputStream()
      var pos = 0
      while (pos < body.size) {
        val subBody: ByteArray
        val idx = Utils.indexOf(body, pos, body.size, "\n}".toByteArray(StandardCharsets.UTF_8))
        if (idx > 0) {
          subBody = ArrayUtils.subarray(body, pos, idx + 2)
          pos = idx + 2
        } else {
          subBody = ArrayUtils.subarray(body, pos, body.size)
          pos = body.size
        }
        val msg = String(subBody, StandardCharsets.UTF_8)
        val data = Protobuf3.encode(msg)
        writeGrpcFrame(rawStream, data)
      }
      return rawStream.toByteArray()
    }

    /**
     * Splits a UTF-8 body into top-level JSON objects. Uses Jackson's tokenizer so `\n` inside
     * string values does not spuriously split (unlike the `"\n}"` heuristic in
     * [encodeSchemalessGrpcBody]).
     */
    private fun splitTopLevelJsonObjects(text: String?): List<String> {
      if (text.isNullOrEmpty()) {
        return Collections.emptyList()
      }
      val out = ArrayList<String>()
      val factory = JsonFactory()
      try {
        factory.createParser(text).use { p ->
          var depth = 0
          var start = -1
          while (p.nextToken() != null) {
            if (p.currentToken() == JsonToken.START_OBJECT) {
              if (depth == 0) {
                start = p.currentLocation.charOffset.toInt()
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

    @Throws(Exception::class)
    private fun encodeGrpcBodyFromJsonChunks(body: ByteArray, type: Descriptor): ByteArray {
      val s = String(body, StandardCharsets.UTF_8)
      var objects = splitTopLevelJsonObjects(s)
      if (objects.isEmpty() && s.trim().isNotEmpty()) {
        objects = Collections.singletonList(s)
      }
      val rawStream = ByteArrayOutputStream()
      for (json in objects) {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) continue
        val data = encodeOneJsonToBinary(trimmed, type)
        writeGrpcFrame(rawStream, data)
      }
      return rawStream.toByteArray()
    }

    @Throws(Exception::class)
    private fun encodeOneJsonToBinary(json: String, type: Descriptor): ByteArray {
      return try {
        val builder = DynamicMessage.newBuilder(type)
        JSON_PARSER.merge(json, builder)
        builder.build().toByteArray()
      } catch (_: Exception) {
        Protobuf3.encode(json)
      }
    }

    @Throws(Exception::class)
    private fun writeGrpcFrame(rawStream: ByteArrayOutputStream, payload: ByteArray) {
      rawStream.write(0)
      rawStream.write(ByteBuffer.allocate(4).putInt(payload.size).array())
      rawStream.write(payload)
    }
  }
}
