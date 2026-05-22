/*
 * Copyright 2025 DeNA Co., Ltd.
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
package core.packetproxy.util

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile

/** fileから1行をUTF-8として読み取る */
fun RandomAccessFile.readUtf8Line(): String? {
  if (this.filePointer >= this.length()) return null

  val buffer = ByteArrayOutputStream()

  while (this.filePointer < this.length()) {
    val b = this.readByte()

    when (b.toInt().toChar()) {
      '\n' -> break

      '\r' -> {
        // CRの次にLFが来ていた場合は追加で読み取る
        val curPos = this.filePointer
        if (curPos < this.length()) {
          val next = this.readByte()
          // LFが来ない場合は読み戻す
          if (next.toInt().toChar() != '\n') this.seek(curPos)
        }
        break
      }

      else -> buffer.write(b.toInt())
    }
  }

  return buffer.toString("UTF-8")
}
