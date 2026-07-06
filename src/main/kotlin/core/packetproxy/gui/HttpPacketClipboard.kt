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
package packetproxy.gui

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import packetproxy.http.Http
import packetproxy.model.Packet

fun copyMethodUrlBody(data: ByteArray, packet: Packet) {
  copyToClipboard(formatMethodUrlBody(data, packet))
}

fun copyBody(data: ByteArray) {
  val http = Http.create(data)
  copyToClipboard(String(http.body, Charsets.UTF_8))
}

fun copyUrl(data: ByteArray, packet: Packet) {
  val http = Http.create(data)
  copyToClipboard(http.getURL(packet.serverPort, packet.useSSL))
}

internal fun formatMethodUrlBody(data: ByteArray, packet: Packet): String {
  val http = Http.create(data)
  return http.method +
    "\t" +
    http.getURL(packet.serverPort, packet.useSSL) +
    "\t" +
    String(http.body, Charsets.UTF_8)
}

private fun copyToClipboard(text: String) {
  val clipboard = Toolkit.getDefaultToolkit().systemClipboard
  val selection = StringSelection(text)
  clipboard.setContents(selection, selection)
}
