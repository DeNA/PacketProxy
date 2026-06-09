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

import picocli.CommandLine.Command
import picocli.CommandLine.HelpCommand

/** picocli コマンドツリーのルート。各サブコマンドに委譲する。 */
@Command(
  name = "packetproxy",
  subcommands =
    [
      ServerCommand::class,
      EncodeCommand::class,
      DecodeCommand::class,
      EncodersCommand::class,
      HelpCommand::class,
    ],
  mixinStandardHelpOptions = true,
  description = ["PacketProxy CLI"],
)
class CliRoot : Runnable {
  override fun run() {
    // サブコマンドなしで呼ばれた場合はヘルプを表示
    picocli.CommandLine(this).usage(System.out)
  }
}
