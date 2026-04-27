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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GrpcServiceRegistryStoreTest {
  private fun resource(classpathPath: String): File {
    val u =
      GrpcServiceRegistryStoreTest::class.java.getResource(classpathPath)
        ?: throw IllegalStateException("missing resource: $classpathPath")
    return File(u.toURI())
  }

  @AfterEach
  fun tearDown() {
    GrpcServiceRegistryStore.getInstance().invalidateAll()
  }

  @Test
  fun getCachesByCanonicalPath() {
    val store = GrpcServiceRegistryStore.getInstance()
    val f = resource("proto/testsvc.desc")
    val a = store.get(f)
    val b = store.get(f)
    assertSame(a, b)
    store.invalidate(f)
  }

  @Test
  fun get_missingFile_throws() {
    val store = GrpcServiceRegistryStore.getInstance()
    assertThrows(Exception::class.java) { store.get(File("/nonexistent/does-not-exist.desc")) }
  }
}
