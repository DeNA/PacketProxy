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
package packetproxy.controller

import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.swing.SwingWorker
import packetproxy.Duplex
import packetproxy.DuplexAsync
import packetproxy.DuplexFactory
import packetproxy.DuplexManager
import packetproxy.EncoderManager
import packetproxy.common.I18nString
import packetproxy.encode.EncodeHTTPBase
import packetproxy.http.Http
import packetproxy.model.OneShotPacket
import packetproxy.model.Packet
import packetproxy.util.Logging.err
import packetproxy.util.Logging.errWithStackTrace

class ResendController private constructor() {
  companion object {
    @Volatile private var instance: ResendController? = null

    @JvmStatic
    @Throws(Exception::class)
    fun getInstance(): ResendController =
      instance ?: synchronized(this) { instance ?: ResendController().also { instance = it } }
  }

  /** レスポンスを受け取って処理する必要がないとき用 */
  @JvmOverloads
  @Throws(Exception::class)
  fun resend(oneshot: OneShotPacket, count: Int = 1, wait: Boolean = false) {
    val worker: SwingWorker<Any?, OneShotPacket> = ResendWorker(oneshot, count)
    worker.execute()
    if (wait && count != 1) {
      try {
        // InterceptでForward x 20した時に先に本体が処理されると困るので待つ
        worker.get(20000, TimeUnit.MILLISECONDS)
      } catch (e: Exception) {
        errWithStackTrace(e)
      }
    }
  }

  /**
   * レスポンスを受け取って処理する必要があるとき用 ResendUsingNewConnectionを無名クラスでextendsしてprocessでList<OneShotPacket>受け取る
   *
   * @param worker
   */
  fun resend(worker: ResendWorker) {
    worker.execute()
  }

  open class ResendWorker
  private constructor(
    private val oneshot: OneShotPacket?,
    private val count: Int,
    private val oneshots: Array<OneShotPacket>?,
  ) : SwingWorker<Any?, OneShotPacket>() {

    // 同じパケットを複数再送
    constructor(oneshot: OneShotPacket, count: Int) : this(oneshot, count, null)

    // 異なるパケットを複数再送
    constructor(oneshots: Array<OneShotPacket>) : this(null, 0, oneshots)

    @Throws(Exception::class)
    override fun doInBackground(): Any? {
      try {
        val onReceived = Consumer<OneShotPacket> { publish(it) }
        val list =
          if (oneshot != null && count > 0) {
            List(count) { DataToBeSend(oneshot, onReceived) }
          } else if (!oneshots.isNullOrEmpty()) {
            oneshots.map { DataToBeSend(it, onReceived) }
          } else {
            err("Resend packet is wrong!")
            return null
          }

        list
          .filter { it.isDirectSend }
          .forEach { sendData ->
            try {
              sendData.send()
            } catch (e: Exception) {
              errWithStackTrace(e)
            }
          }
        list
          .filter { !it.isDirectSend }
          .parallelStream()
          .forEach { sendData ->
            try {
              sendData.send()
            } catch (e: Exception) {
              errWithStackTrace(e)
            }
          }
      } catch (e: SocketTimeoutException) {
        err("Resend Connection is timeout!")
        err("All resend packets are dropped.")
        errWithStackTrace(e)
        return null
      } catch (e: Exception) {
        errWithStackTrace(e)
        throw e
      }
      return null
    }

    private class DataToBeSend(
      private val oneshot: OneShotPacket,
      private val onReceived: Consumer<OneShotPacket>,
    ) {
      private var duplex: Duplex? = null
      private var preparedData: ByteArray? = null
      private var isSync = false
      var isDirectSend = false
        private set

      init {
        val encoder = EncoderManager.getInstance().createInstance(oneshot.encoder, oneshot.alpn)
        if (!encoder.useNewConnectionForResend() && !encoder.useNewEncoderForResend()) {
          isDirectSend = true
          duplex = DuplexManager.getInstance().getDuplex(oneshot.conn)
          preparedData = oneshot.data
        } else {
          prepareNewDuplex(encoder.useNewConnectionForResend())
        }
      }

      @Throws(Exception::class)
      private fun prepareNewDuplex(useNewConnection: Boolean) {
        if (useNewConnection) {
          duplex = DuplexFactory.createDuplexSyncFromOneShotPacket(oneshot)
          isSync = false
        } else {
          val originalDuplex = DuplexManager.getInstance().getDuplex(oneshot.conn)
          if (originalDuplex == null) {
            err(
              I18nString.get(
                "[Error] tried to resend packets, but the connection was already closed."
              )
            )
            return
          }
          val newDuplex = DuplexFactory.createDuplexFromOriginalDuplex(originalDuplex, oneshot)
          duplex = newDuplex
          if (newDuplex is DuplexAsync) {
            newDuplex.start()
          }
          isSync = true
        }
        preparedData = duplex!!.prepareFastSend(oneshot.data)
      }

      @Throws(Exception::class)
      fun send() {
        val duplex = duplex ?: return

        if (isDirectSend) {
          duplex.sendToServer(preparedData)
          onReceived.accept(createResult(alreadyConnectedMessage()))
          return
        }

        duplex.execFastSend(preparedData)
        if (isSync) {
          onReceived.accept(createResult(alreadyConnectedMessage()))
        } else {
          var data = duplex.receive()

          /* 100 Continue 対策 */
          val encoder = EncoderManager.getInstance().createInstance(oneshot.encoder, oneshot.alpn)
          if (
            encoder is EncodeHTTPBase && encoder.httpVersion == EncodeHTTPBase.HTTPVersion.HTTP1
          ) {
            while (Http.create(data).statusCode == "100") {
              data = duplex.receive()
            }
          }

          onReceived.accept(createResult(data))
        }
      }

      private fun alreadyConnectedMessage(): ByteArray =
        I18nString.get(
            "In case that packets were resend to already connected socket, results can't be displayed in this window. See the history window instead."
          )
          .toByteArray()

      private fun createResult(data: ByteArray): OneShotPacket =
        OneShotPacket(
          oneshot.id,
          oneshot.listenPort,
          oneshot.client,
          oneshot.server,
          oneshot.serverName,
          oneshot.useSSL,
          data,
          oneshot.encoder,
          oneshot.alpn,
          Packet.Direction.SERVER,
          oneshot.conn,
          oneshot.group,
        )
    }
  }
}
