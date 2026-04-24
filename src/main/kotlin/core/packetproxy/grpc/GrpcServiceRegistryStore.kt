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

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches [GrpcServiceRegistry] per descriptor file path so short-lived encoders do not re-parse
 * `.desc`.
 */
class GrpcServiceRegistryStore private constructor() {
  private val cache = ConcurrentHashMap<String, GrpcServiceRegistry>()

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
      val hit = GrpcServiceRegistry(DescriptorSetLoader.loadAndBuild(descFile))
      cache[key] = hit
      return hit
    }
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
