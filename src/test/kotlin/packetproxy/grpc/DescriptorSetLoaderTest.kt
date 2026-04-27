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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DescriptorSetLoaderTest {
  private fun resource(classpathPath: String): File {
    val u =
      DescriptorSetLoaderTest::class.java.getResource(classpathPath)
        ?: throw IllegalStateException("missing resource: $classpathPath")
    return File(u.toURI())
  }

  @Test
  fun loadAndBuild_missingFile_throws() {
    assertThrows(Exception::class.java) {
      DescriptorSetLoader.loadAndBuild(File("/nonexistent/path.desc"))
    }
  }

  @Test
  fun loadAndBuild_invalidBytes_throws() {
    val tmp = Files.createTempFile("bad", ".desc").toFile()
    Files.writeString(tmp.toPath(), "not-a-protobuf-descriptor", StandardCharsets.UTF_8)
    assertThrows(Exception::class.java) { DescriptorSetLoader.loadAndBuild(tmp) }
  }

  @Test
  fun loadAndBuild_withIncludeImports_ok() {
    val f = resource("proto/multidir/multi.desc")
    val list = DescriptorSetLoader.loadAndBuild(f)
    assertFalse(list.isEmpty())
  }

  // multi_without_imports.desc was built without --include_imports, so transitive deps are missing.
  // loadAndBuild must detect this and throw rather than silently producing an incomplete registry.
  @Test
  fun loadAndBuild_withoutIncludeImports_throws() {
    val f = resource("proto/multidir/multi_without_imports.desc")
    assertThrows(IllegalStateException::class.java) { DescriptorSetLoader.loadAndBuild(f) }
  }

  @Test
  fun loadAndBuild_testsvc_containsGreeterService() {
    val f = resource("proto/testsvc.desc")
    val list = DescriptorSetLoader.loadAndBuild(f)
    assertTrue(list.any { it.findServiceByName("Greeter") != null })
  }
}
