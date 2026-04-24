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

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import org.apache.commons.io.IOUtils

/**
 * Runs the `protoc` binary from `PATH` to emit a
 * [com.google.protobuf.DescriptorProtos.FileDescriptorSet].
 */
class ProtocRunner private constructor() {
  class Result(
    @JvmField val ok: Boolean,
    @JvmField val exitCode: Int,
    @JvmField val stdout: String,
    @JvmField val stderr: String,
    @JvmField val descFile: File,
  )

  companion object {
    private val DEFAULT_DESC_DIR = File(System.getProperty("user.home"), ".packetproxy/grpc_desc")

    @JvmStatic
    @Throws(Exception::class)
    fun checkProtocOnPath() {
      val pb = ProcessBuilder("protoc", "--version")
      pb.redirectErrorStream(true)
      val p = pb.start()
      val out = drainToString(p.inputStream)
      val finished = p.waitFor(30, TimeUnit.SECONDS)
      if (!finished) {
        p.destroyForcibly()
        throw Exception("protoc が応答しません（タイムアウト）。PATH に protoc が含まれているか確認してください。")
      }
      if (p.exitValue() != 0) {
        throw Exception("protoc の実行に失敗しました: $out")
      }
    }

    /**
     * Allocates an output path under `~/.packetproxy/grpc_desc/` and runs `protoc`. The file name
     * encodes the optional [serverId] so callers can correlate the output with a server entry.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun run(protos: List<File>, includes: List<File>, serverId: Int?): Result {
      if (!DEFAULT_DESC_DIR.exists() && !DEFAULT_DESC_DIR.mkdirs()) {
        throw IllegalStateException("Cannot create directory: ${DEFAULT_DESC_DIR.absolutePath}")
      }
      val ts = System.currentTimeMillis()
      val name = if (serverId != null) "server_${serverId}_$ts.desc" else "new_$ts.desc"
      return run(protos, includes, File(DEFAULT_DESC_DIR, name))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun run(protos: List<File>, includes: List<File>, outDesc: File): Result {
      val cmd = ArrayList<String>()
      cmd.add("protoc")
      cmd.add("--include_imports")
      cmd.add("--descriptor_set_out=${outDesc.absolutePath}")
      for (inc in includes) {
        cmd.add("-I${inc.absolutePath}")
      }
      for (proto in protos) {
        cmd.add(proto.absolutePath)
      }
      val pb = ProcessBuilder(cmd)
      pb.redirectErrorStream(false)
      val p = pb.start()
      val outBuf = ByteArrayOutputStream()
      val errBuf = ByteArrayOutputStream()
      val tout = Thread { copyQuietly(p.inputStream, outBuf) }
      val terr = Thread { copyQuietly(p.errorStream, errBuf) }
      tout.start()
      terr.start()
      val finished = p.waitFor(5, TimeUnit.MINUTES)
      if (!finished) {
        p.destroyForcibly()
        tout.join(2000)
        terr.join(2000)
        return Result(
          false,
          -1,
          outBuf.toString(StandardCharsets.UTF_8),
          errBuf.toString(StandardCharsets.UTF_8) + "\n[timeout]",
          outDesc,
        )
      }
      tout.join()
      terr.join()
      val code = p.exitValue()
      return Result(
        code == 0,
        code,
        outBuf.toString(StandardCharsets.UTF_8),
        errBuf.toString(StandardCharsets.UTF_8),
        outDesc,
      )
    }

    private fun copyQuietly(input: InputStream?, dest: ByteArrayOutputStream) {
      try {
        if (input != null) IOUtils.copy(input, dest)
      } catch (_: Exception) {}
    }

    @Throws(Exception::class)
    private fun drainToString(input: InputStream): String {
      return String(IOUtils.toByteArray(input), StandardCharsets.UTF_8)
    }
  }
}
