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
package packetproxy

import java.net.ServerSocket
import packetproxy.common.I18nString
import packetproxy.model.ListenPort
import packetproxy.util.Logging

object ProxyFactory {
  @JvmStatic
  @Throws(Exception::class)
  fun create(listenInfo: ListenPort): Proxy {
    val type = listenInfo.getType()
    val port = listenInfo.getPort()

    Logging.log("type is $type")
    Logging.log(I18nString.get("Start listening port %d.", port))

    return when (type) {
      ListenPort.TYPE.UDP_FORWARDER -> ProxyUDPForward(listenInfo)
      ListenPort.TYPE.QUIC_FORWARDER -> ProxyQuicForward(listenInfo)
      ListenPort.TYPE.QUIC_TRANSPARENT_PROXY -> ProxyQuicTransparent(listenInfo)

      else -> {
        val listenSocket = ServerSocket(port)

        when (type) {
          ListenPort.TYPE.HTTP_PROXY -> ProxyHttp(listenSocket, listenInfo)
          ListenPort.TYPE.SSL_FORWARDER -> ProxySSLForward(listenSocket, listenInfo)
          ListenPort.TYPE.HTTP_TRANSPARENT_PROXY -> ProxyHttpTransparent(listenSocket, listenInfo)
          ListenPort.TYPE.SSL_TRANSPARENT_PROXY -> ProxySSLTransparent(listenSocket, listenInfo)

          else -> {
            listenSocket.setReuseAddress(true)

            when (type) {
              ListenPort.TYPE.XMPP_SSL_FORWARDER -> ProxyXmppSSLForward(listenSocket, listenInfo)
              else -> ProxyForward(listenSocket, listenInfo)
            }
          }
        }
      }
    }
  }
}
