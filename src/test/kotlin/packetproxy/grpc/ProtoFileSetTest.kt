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
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtoFileSetTest {
  private fun resource(classpathPath: String): File {
    val u =
      ProtoFileSetTest::class.java.getResource(classpathPath)
        ?: throw IllegalStateException("missing resource: $classpathPath")
    return File(u.toURI())
  }

  @Test
  fun addFile_rejectsNonProto() {
    val set = ProtoFileSet()
    val tmp = Files.createTempFile("x", ".txt").toFile()
    assertFalse(set.addFile(tmp))
  }

  @Test
  fun addFile_deduplicatesByCanonicalPath() {
    val set = ProtoFileSet()
    val f = resource("/proto/testsvc.proto")
    assertTrue(set.addFile(f))
    assertFalse(set.addFile(f))
    assertEquals(1, set.list().size)
  }

  @Test
  fun includePaths_uniqueParents() {
    val set = ProtoFileSet()
    set.addFile(resource("/proto/testsvc.proto"))
    set.addFile(resource("/proto/multidir/common.proto"))
    val inc = set.includePaths()
    assertEquals(2, inc.size)
  }

  @Test
  fun addDirectoryShallow_nonRecursive() {
    val set = ProtoFileSet()
    val n = set.addDirectoryShallow(resource("/proto/testsvc.proto").parentFile)
    assertTrue(n >= 1)
  }
}
