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

import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.Descriptors.DescriptorValidationException
import com.google.protobuf.Descriptors.FileDescriptor
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import packetproxy.model.ListenPort
import packetproxy.model.ListenPorts
import packetproxy.model.Server
import packetproxy.model.Servers

class GrpcServiceRegistryStore private constructor() {
  private val cache = ConcurrentHashMap<String, GrpcServiceRegistry>()

  /** Transparent proxy では authority がリスナーアドレスになるため、Servers → ListenPort の順でフォールバックする */
  fun getByAuthority(authority: String?): GrpcServiceRegistry? {
    if (authority.isNullOrBlank()) return null
    return try {
      val parsed = parseAuthorityHostPort(authority.trim()) ?: return null
      val (host, port) = parsed
      var server = Servers.getInstance().queryByHostNameAndPort(host, port)
      if (server == null) {
        try {
          val addr = InetSocketAddress(host, port)
          server = Servers.getInstance().queryByAddress(addr)
        } catch (_: Exception) {
          // unresolved hostname / invalid socket
        }
      }
      if (server == null) {
        server = tryResolveServerViaListenPort(port, ListenPorts.getInstance())
      }
      if (server == null) return null
      val path = server.descriptorPath?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
      val f = File(path)
      if (!f.isFile()) return null
      get(f)
    } catch (_: Exception) {
      null
    }
  }

  internal fun tryResolveServerViaListenPort(port: Int, listenPorts: ListenPorts): Server? {
    return try {
      val listenPort = listenPorts.queryEnabledByPort(ListenPort.Protocol.TCP, port) ?: return null
      listenPort.getServer()
    } catch (_: Exception) {
      null
    }
  }

  internal fun parseAuthorityHostPort(authority: String): Pair<String, Int>? {
    if (authority.isEmpty()) return null
    if (authority.startsWith('[')) {
      val close = authority.indexOf(']')
      if (close < 0) return null
      val host = authority.substring(1, close)
      val rest = authority.substring(close + 1)
      val port =
        if (rest.startsWith(":")) {
          rest.substring(1).toIntOrNull() ?: return null
        } else {
          443
        }
      return Pair(host, port)
    }
    val colonIdx = authority.lastIndexOf(':')
    if (colonIdx < 0) {
      return Pair(authority, 443)
    }
    if (colonIdx == authority.length - 1) {
      return null
    }
    val hostPart = authority.substring(0, colonIdx)
    val portPart = authority.substring(colonIdx + 1)
    val port = portPart.toIntOrNull()
    return if (port != null && portPart.isNotEmpty() && portPart.all { it.isDigit() }) {
      Pair(hostPart, port)
    } else {
      Pair(authority, 443)
    }
  }

  @Throws(Exception::class)
  fun get(descFile: File?): GrpcServiceRegistry {
    if (descFile == null) {
      throw IllegalArgumentException("descFile is null")
    }
    val key = descFile.canonicalPath
    cache[key]?.let {
      return it
    }
    synchronized(this) {
      cache[key]?.let {
        return it
      }
      val hit = GrpcServiceRegistry(loadAndBuild(descFile))
      cache[key] = hit
      return hit
    }
  }

  @Throws(IOException::class, DescriptorValidationException::class, IllegalStateException::class)
  private fun loadAndBuild(descFile: File): List<FileDescriptor> {
    val bytes = Files.readAllBytes(descFile.toPath())
    val fds = FileDescriptorSet.parseFrom(bytes)
    val known = HashMap<String, FileDescriptor>()
    val ordered = ArrayList<FileDescriptor>()
    for (fdp in fds.fileList) {
      val deps =
        Array(fdp.dependencyCount) { i ->
          val depName = fdp.getDependency(i)
          known[depName]
            ?: throw IllegalStateException(
              "Missing dependency '$depName' while building '${fdp.name}'. " +
                "Re-generate with: protoc --include_imports --descriptor_set_out=out.desc -I... your.proto"
            )
        }
      val built = FileDescriptor.buildFrom(fdp, deps)
      ordered.add(built)
      known[built.name] = built
    }
    return ordered
  }

  fun invalidate(descFile: File?) {
    if (descFile == null) return
    try {
      cache.remove(descFile.canonicalPath)
    } catch (_: Exception) {
      cache.remove(descFile.absolutePath)
    }
  }

  fun invalidateAll() {
    cache.clear()
  }

  companion object {
    private val INSTANCE = GrpcServiceRegistryStore()

    @JvmStatic fun getInstance(): GrpcServiceRegistryStore = INSTANCE
  }
}
