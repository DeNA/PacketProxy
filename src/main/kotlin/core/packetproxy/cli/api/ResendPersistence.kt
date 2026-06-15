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
package packetproxy.cli.api

import java.util.concurrent.atomic.AtomicLong
import packetproxy.model.OneShotPacket
import packetproxy.model.Packets

/**
 * 再送・VulCheck で送受信したパケットを通信履歴（packets テーブル）に保存し、 採番された ID を返すためのヘルパー。
 *
 * REST API の呼び出し元は、返却された packetId を使って `GET /api/packets/{id}` で 送信内容（sentData /
 * decodedData）と応答（receivedData）を後から取得できる。
 *
 * 1 回の API 呼び出し（バッチ）に属する全パケットは同一の group を共有する。 リクエストと応答の対応付けは、API レスポンスの requestPacketId /
 * responsePacketId で 1:1 に表現する。
 */
internal object ResendPersistence {

  // バッチ単位で一意な group 値。起動時刻を種にして実通信由来の group と衝突しにくくする。
  private val batchGroup = AtomicLong(System.currentTimeMillis())

  /** 新しいバッチ用の group 値を払い出す。 */
  fun nextGroup(): Long = batchGroup.incrementAndGet()

  /**
   * 送信したリクエストを履歴に保存し、採番された packet ID を返す。 direction は元パケットを引き継ぐ（通常 CLIENT）。resend フラグを立て、 バッチの
   * group を付与する。
   */
  fun persistRequest(sent: OneShotPacket, group: Long): Int {
    val packet = sent.toPacket()
    packet.setSentData(sent.getData())
    packet.setResend()
    packet.setGroup(group)
    Packets.getInstance().updateSync(packet)
    return packet.getId()
  }

  /** サーバーからの応答を履歴に保存し、採番された packet ID を返す。 応答が取得できなかった場合（タイムアウト、既存コネクションへの再送など）は null。 */
  fun persistResponse(received: OneShotPacket?, group: Long): Int? {
    if (received == null) return null
    val packet = received.toPacket() // direction SERVER, decoded_data = 応答
    packet.setReceivedData(received.getData())
    packet.setResend()
    packet.setGroup(group)
    Packets.getInstance().updateSync(packet)
    return packet.getId()
  }
}
