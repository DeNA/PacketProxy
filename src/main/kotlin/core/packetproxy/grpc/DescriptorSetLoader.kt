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
import java.nio.file.Files

/**
 * Loads a `.desc` file produced by `protoc --include_imports` and builds a dependency-ordered list
 * of [FileDescriptor] instances.
 */
class DescriptorSetLoader private constructor() {
  companion object {
    /**
     * Reads [descFile], parses the [FileDescriptorSet], and resolves each file's dependencies in
     * order. Requires `protoc --include_imports` so that all transitive deps are present.
     */
    @JvmStatic
    @Throws(IOException::class, DescriptorValidationException::class, IllegalStateException::class)
    fun loadAndBuild(descFile: File): List<FileDescriptor> {
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
  }
}
