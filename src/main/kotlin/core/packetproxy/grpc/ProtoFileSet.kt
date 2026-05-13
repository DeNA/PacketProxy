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
import java.util.ArrayList
import java.util.LinkedHashMap
import org.apache.commons.io.FilenameUtils

/** Ordered, de-duplicated set of `.proto` paths for `protoc` invocation. */
class ProtoFileSet {
  private val canonicalToFile = LinkedHashMap<String, File>()

  @Throws(Exception::class)
  fun addFile(file: File?): Boolean {
    if (file == null || !file.isFile) return false
    if (!"proto".equals(FilenameUtils.getExtension(file.name), ignoreCase = true)) {
      return false
    }
    val key = file.canonicalPath
    if (canonicalToFile.containsKey(key)) return false
    canonicalToFile[key] = file
    return true
  }

  @Throws(Exception::class)
  fun addDirectoryShallow(dir: File?): Int {
    if (dir == null || !dir.isDirectory) return 0
    val children = dir.listFiles() ?: return 0
    var added = 0
    for (child in children) {
      if (addFile(child)) added++
    }
    return added
  }

  @Throws(Exception::class)
  fun remove(file: File?): Boolean {
    if (file == null) return false
    return canonicalToFile.remove(file.canonicalPath) != null
  }

  fun list(): List<File> = ArrayList(canonicalToFile.values)

  /** Unique parent directories of added protos, in insertion order (for `protoc -I`). */
  @Throws(Exception::class)
  fun includePaths(): List<File> {
    val dirs = LinkedHashMap<String, File>()
    for (f in canonicalToFile.values) {
      val parent = f.parentFile ?: continue
      dirs[parent.canonicalPath] = parent
    }
    return ArrayList(dirs.values)
  }
}
