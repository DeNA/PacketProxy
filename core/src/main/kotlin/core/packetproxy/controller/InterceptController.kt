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

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import packetproxy.model.InterceptModel
import packetproxy.model.InterceptOptions
import packetproxy.model.Packet
import packetproxy.model.Server

/**
 * パケットのインターセプト（傍受・改ざん・廃棄）を制御する。
 *
 * プロキシパイプライン側（received）でパケットを捕捉し、UI側（forward/drop）からの 操作決定を待機する。InterceptModel を介して UI に状態を通知し、
 * InterceptOptions のルールに基づいて対象パケットをフィルタリングする。
 * - suspend fun received(): 新プロキシ向け。コルーチンをサスペンドして UI 操作を待つ。
 * - receivedBlocking(): DuplexFactory.java 向けの暫定ブリッジ（Deprecated）。
 */
class InterceptController
private constructor(
  private val interceptModel: InterceptModel = InterceptModel.getInstance(),
  private val resendController: ResendController = ResendController.getInstance(),
) {
  companion object {
    @Volatile private var instance: InterceptController? = null

    @JvmStatic
    @Throws(Exception::class)
    fun getInstance(): InterceptController =
      instance ?: synchronized(this) { instance ?: InterceptController().also { instance = it } }
  }

  private sealed class InterceptDecision {
    data class Forward(val data: ByteArray) : InterceptDecision()

    data class ForwardMultiple(val data: ByteArray) : InterceptDecision()

    data object Drop : InterceptDecision()
  }

  private val mutex = Mutex()
  private var pendingDeferred: CompletableDeferred<InterceptDecision>? = null

  fun enableInterceptMode() {
    interceptModel.enableInterceptMode()
  }

  fun disableInterceptMode(data: ByteArray) {
    pendingDeferred?.complete(InterceptDecision.Forward(data))
    interceptModel.disableInterceptMode()
  }

  fun forward(data: ByteArray) {
    pendingDeferred?.complete(InterceptDecision.Forward(data))
  }

  @Suppress("FunctionName")
  fun forward_multiple(data: ByteArray) {
    pendingDeferred?.complete(InterceptDecision.ForwardMultiple(data))
  }

  fun drop() {
    pendingDeferred?.complete(InterceptDecision.Drop)
  }

  /**
   * suspend 版
   *
   * 戻り値:
   * - None = drop（ユーザーが意図した廃棄。エラーではない）
   * - Some = forward（通過。データはユーザーが改ざんしている可能性あり）
   */
  suspend fun received(
    data: ByteArray,
    server: Server?,
    clientPacket: Packet,
    serverPacket: Packet? = null,
  ): Option<ByteArray> {
    val targetPacket = serverPacket ?: clientPacket

    if (!isInterceptTarget(server, clientPacket, serverPacket)) return Some(data)

    return mutex.withLock {
      val deferred = CompletableDeferred<InterceptDecision>()
      pendingDeferred = deferred
      interceptModel.setData(data, clientPacket, serverPacket)
      try {
        when (val decision = deferred.await()) {
          is InterceptDecision.Drop -> None
          is InterceptDecision.Forward -> Some(decision.data)
          is InterceptDecision.ForwardMultiple -> {
            resendController.resend(targetPacket.getOneShotPacket(decision.data), 19, true)
            targetPacket.setResend()
            Some(decision.data)
          }
        }
      } finally {
        interceptModel.clearData()
        pendingDeferred = null
      }
    }
  }

  /**
   * Java 互換のブロッキングラッパー。DuplexFactory.java からの呼び出しのために存在する。
   *
   * TODO: DuplexFactory が Kotlin 化され suspend fun received() を直接呼べるようになった時点で削除すること。 依存先:
   *   DuplexFactory.java（2箇所）
   *     - onClientChunkReceived()
   *     - onServerChunkReceived()
   */
  @JvmOverloads
  @Deprecated(
    "Use suspend fun received() instead. Remove when DuplexFactory.java is migrated to Kotlin.",
    level = DeprecationLevel.WARNING,
  )
  fun receivedBlocking(
    data: ByteArray,
    server: Server?,
    clientPacket: Packet,
    serverPacket: Packet? = null,
  ): ByteArray = runBlocking {
    received(data, server, clientPacket, serverPacket).fold({ ByteArray(0) }) { it }
  }

  private fun isInterceptTarget(
    server: Server?,
    clientPacket: Packet,
    serverPacket: Packet?,
  ): Boolean {
    if (!interceptModel.isInterceptEnabled) return false

    if (InterceptOptions.getInstance().isEnabled) {
      return if (serverPacket == null) {
        InterceptOptions.getInstance().interceptOnRequest(server, clientPacket)
      } else {
        InterceptOptions.getInstance().interceptOnResponse(server, clientPacket, serverPacket)
      }
    }

    return true
  }
}
