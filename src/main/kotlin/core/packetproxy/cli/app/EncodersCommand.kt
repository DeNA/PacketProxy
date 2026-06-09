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

import java.util.concurrent.Callable
import packetproxy.EncoderManager
import packetproxy.util.LogMode
import packetproxy.util.Logging
import picocli.CommandLine.Command

/** 利用可能なエンコーダ名の一覧を stdout に出力する。 */
@Command(
  name = "encoders",
  mixinStandardHelpOptions = true,
  description = ["List all available encoder names"],
)
class EncodersCommand : Callable<Int> {

  override fun call(): Int {
    Logging.init(LogMode.SILENT)

    val names =
      try {
        EncoderManager.getInstance().getEncoderNameList()
      } catch (e: Exception) {
        System.err.println("Failed to load encoders: ${e.message}")
        return 1
      }

    names.forEach { println(it) }
    return 0
  }
}
