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
package packetproxy.cli.app

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import packetproxy.EncoderManager
import packetproxy.util.LogMode
import packetproxy.util.Logging

/** encode/decode ワンショット変換のコア処理。stdin → エンコーダ → stdout のパイプフィルタ。 */
object Codec {

  enum class Direction {
    ENCODE_REQUEST,
    ENCODE_RESPONSE,
    DECODE_REQUEST,
    DECODE_RESPONSE,
  }

  /**
   * @param encoderName 使用するエンコーダ名。不明な名前の場合は stderr に一覧を出して 1 を返す。
   * @param direction 変換方向。
   * @param text true の場合 UTF-8 テキストとして出力する（末尾改行を保証）。
   * @param inFile null の場合は System.in を使う。
   * @param outFile null の場合は System.out を使う。
   * @return 0=成功, 1=エラー
   */
  fun run(
    encoderName: String,
    direction: Direction,
    text: Boolean,
    inFile: File?,
    outFile: File?,
  ): Int {
    Logging.init(LogMode.SILENT)

    val manager =
      try {
        EncoderManager.getInstance()
      } catch (e: Exception) {
        System.err.println("Failed to load encoder manager: ${e.message}")
        return 1
      }

    val encoder = manager.createInstance(encoderName, null)
    if (encoder == null) {
      val available = manager.getEncoderNameList().joinToString("\n  ")
      System.err.println("Unknown encoder: '$encoderName'")
      System.err.println("Available encoders:\n  $available")
      return 1
    }

    val input: ByteArray =
      try {
        (inFile?.inputStream() ?: System.`in`).use(InputStream::readBytes)
      } catch (e: Exception) {
        System.err.println("Failed to read input: ${e.message}")
        return 1
      }

    val output: ByteArray =
      try {
        when (direction) {
          Direction.ENCODE_REQUEST -> encoder.encodeClientRequest(input)
          Direction.ENCODE_RESPONSE -> encoder.encodeServerResponse(input)
          Direction.DECODE_REQUEST -> encoder.decodeClientRequest(input)
          Direction.DECODE_RESPONSE -> encoder.decodeServerResponse(input)
        }
      } catch (e: Exception) {
        System.err.println("Encoding error: ${e.message}")
        return 1
      }

    try {
      if (outFile != null) {
        outFile.writeBytes(output)
      } else if (text) {
        val str = String(output, Charsets.UTF_8)
        print(str)
        if (!str.endsWith("\n")) println()
      } else {
        (System.out as OutputStream).write(output)
        System.out.flush()
      }
    } catch (e: Exception) {
      System.err.println("Failed to write output: ${e.message}")
      return 1
    }

    return 0
  }
}
