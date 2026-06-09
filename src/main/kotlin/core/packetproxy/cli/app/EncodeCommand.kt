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
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/** stdin を受け取り、指定エンコーダで encode して stdout へ出力するワンショットコマンド。 */
@Command(
  name = "encode",
  mixinStandardHelpOptions = true,
  description = ["Encode stdin using the specified encoder and write result to stdout"],
)
class EncodeCommand : Callable<Int> {

  @Option(
    names = ["--encoder", "-e"],
    required = true,
    description = ["Encoder name. Use 'encoders' subcommand to list available names."],
  )
  lateinit var encoderName: String

  @Option(
    names = ["--response"],
    description = ["Use server-response direction (encodeServerResponse). Default: client-request."],
  )
  var response: Boolean = false

  @Option(
    names = ["--text"],
    description =
      ["Treat output as UTF-8 text (adds trailing newline if missing). Default: binary."],
  )
  var text: Boolean = false

  @Option(names = ["--in"], description = ["Input file. Default: stdin."]) var inFile: File? = null

  @Option(names = ["--out"], description = ["Output file. Default: stdout."])
  var outFile: File? = null

  override fun call(): Int =
    Codec.run(
      encoderName = encoderName,
      direction = if (response) Codec.Direction.ENCODE_RESPONSE else Codec.Direction.ENCODE_REQUEST,
      text = text,
      inFile = inFile,
      outFile = outFile,
    )
}
