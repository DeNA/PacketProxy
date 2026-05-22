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

import java.util.HashMap

/**
 * リクエストとレスポンスのパケットペアリングを管理するサービス。
 *
 * GUIHistory から EDT 上でのみ呼び出される前提のため、同期コレクションは使用しない。
 */
class PacketPairingService {
  private companion object {
    private const val NO_RESPONSE_PACKET_ID = -1
    private const val NO_REQUEST_PACKET_ID = -1
  }

  // グループIDと行番号のマッピング（リクエスト行を追跡）
  private val groupRow: MutableMap<Long, Int> = HashMap()
  // レスポンスが既にマージされているグループID
  private val groupHasResponse = mutableSetOf<Long>()
  // レスポンスパケットIDとリクエストパケットIDのマッピング（マージされた行用）
  private val responseToRequestId: MutableMap<Int, Int> = HashMap()
  // リクエストパケットIDとレスポンスパケットIDのマッピング（マージされた行用）
  private val requestToResponseId: MutableMap<Int, Int> = HashMap()
  // グループIDごとのパケット数（3個以上でマージしない）
  private val groupPacketCount: MutableMap<Long, Int> = HashMap()
  // グループIDごとのCLIENTパケット数（2個以上でストリーミングと判定）
  private val groupClientPacketCount: MutableMap<Long, Int> = HashMap()

  /** すべてのペアリング情報をクリアする */
  fun clear() {
    groupRow.clear()
    groupHasResponse.clear()
    responseToRequestId.clear()
    requestToResponseId.clear()
    groupPacketCount.clear()
    groupClientPacketCount.clear()
  }

  /**
   * グループIDに対応する行インデックスを登録する
   *
   * @param groupId グループID
   * @param rowIndex 行インデックス
   */
  fun registerGroupRow(groupId: Long, rowIndex: Int) {
    groupRow[groupId] = rowIndex
  }

  /**
   * グループIDに対応する行インデックスを取得する
   *
   * @param groupId グループID
   * @return 行インデックス、存在しない場合はnull
   */
  fun getRowForGroup(groupId: Long): Int? {
    return groupRow[groupId]
  }

  /**
   * グループIDが登録されているか確認する
   *
   * @param groupId グループID
   * @return 登録されている場合true
   */
  fun containsGroup(groupId: Long): Boolean {
    return groupRow.containsKey(groupId)
  }

  /**
   * グループにレスポンスがマージされたことを記録する
   *
   * @param groupId グループID
   */
  fun markGroupHasResponse(groupId: Long) {
    groupHasResponse.add(groupId)
  }

  /**
   * グループにレスポンスがマージされているか確認する
   *
   * @param groupId グループID
   * @return マージされている場合true
   */
  fun hasResponse(groupId: Long): Boolean {
    return groupHasResponse.contains(groupId)
  }

  /**
   * レスポンスパケットIDとリクエストパケットIDのペアリングを登録する
   *
   * @param responsePacketId レスポンスパケットID
   * @param requestPacketId リクエストパケットID
   */
  fun registerPairing(responsePacketId: Int, requestPacketId: Int) {
    responseToRequestId[responsePacketId] = requestPacketId
    requestToResponseId[requestPacketId] = responsePacketId
  }

  /**
   * レスポンスパケットIDに対応するリクエストパケットIDを取得する
   *
   * @param responsePacketId レスポンスパケットID
   * @return リクエストパケットID、存在しない場合は-1
   */
  fun getRequestIdForResponse(responsePacketId: Int): Int {
    return responseToRequestId[responsePacketId] ?: NO_REQUEST_PACKET_ID
  }

  /**
   * レスポンスパケットIDがペアリングに登録されているか確認する
   *
   * @param responsePacketId レスポンスパケットID
   * @return 登録されている場合true
   */
  fun containsResponsePairing(responsePacketId: Int): Boolean {
    return responseToRequestId.containsKey(responsePacketId)
  }

  /**
   * リクエストパケットIDに対応するレスポンスパケットIDを取得する マージされた行の場合のみ有効
   *
   * @param requestPacketId リクエストパケットID
   * @return レスポンスパケットID、存在しない場合は-1
   */
  fun getResponsePacketIdForRequest(requestPacketId: Int): Int {
    return requestToResponseId[requestPacketId] ?: NO_RESPONSE_PACKET_ID
  }

  /**
   * 選択された行がマージされた行（リクエスト+レスポンス）かどうかを判定
   *
   * @param packetId パケットID
   * @return マージされた行の場合true
   */
  fun isMergedRow(packetId: Int): Boolean {
    return getResponsePacketIdForRequest(packetId) != NO_RESPONSE_PACKET_ID
  }

  /**
   * グループのパケット数をインクリメントする
   *
   * @param groupId グループID
   * @return インクリメント後のパケット数
   */
  fun incrementGroupPacketCount(groupId: Long): Int {
    return groupPacketCount.compute(groupId) { _, currentCount -> (currentCount ?: 0) + 1 } ?: 0
  }

  /**
   * グループのパケット数を取得する
   *
   * @param groupId グループID
   * @return パケット数
   */
  fun getGroupPacketCount(groupId: Long): Int {
    return groupPacketCount[groupId] ?: 0
  }

  /**
   * グループがマージ可能かどうかを判定する
   *
   * 以下の両方を満たす場合のみマージ可能：
   * - 総パケット数が2以下（3個以上はストリーミング等）
   * - CLIENTパケット数が1以下（2個以上はストリーミングと判断し、マージしない）
   *     - gRPCストリーミングでは同一グループ内にHEADERSフレームとDATAフレームで2つのCLIENTパケットが存在するため
   *
   * @param groupId グループID
   * @return マージ可能な場合true
   */
  fun isGroupMergeable(groupId: Long): Boolean {
    return getGroupPacketCount(groupId) <= 2 && getGroupClientPacketCount(groupId) < 2
  }

  /**
   * グループのマージ状態を解除する（ストリーミング通信で3つ目以降のパケットが来た場合に使用）
   *
   * @param groupId グループID
   */
  fun unmergeGroup(groupId: Long) {
    groupHasResponse.remove(groupId)
  }

  /**
   * 指定されたリクエストパケットIDに対応するレスポンスのペアリングを解除する
   *
   * @param requestPacketId リクエストパケットID
   * @return 解除されたレスポンスパケットID、存在しない場合は-1
   */
  fun unregisterPairingByRequestId(requestPacketId: Int): Int {
    val responsePacketId = getResponsePacketIdForRequest(requestPacketId)
    if (responsePacketId != NO_RESPONSE_PACKET_ID) {
      responseToRequestId.remove(responsePacketId)
      requestToResponseId.remove(requestPacketId)
    }
    return responsePacketId
  }

  /**
   * グループのCLIENTパケット数をインクリメントする
   *
   * @param groupId グループID
   * @return インクリメント後のCLIENTパケット数
   */
  fun incrementGroupClientPacketCount(groupId: Long): Int {
    return groupClientPacketCount.compute(groupId) { _, currentCount -> (currentCount ?: 0) + 1 }
      ?: 0
  }

  /**
   * グループのCLIENTパケット数を取得する
   *
   * @param groupId グループID
   * @return CLIENTパケット数
   */
  fun getGroupClientPacketCount(groupId: Long): Int {
    return groupClientPacketCount[groupId] ?: 0
  }

  /**
   * グループがストリーミングかどうかを判定する CLIENTパケット数が2以上の場合はストリーミングと判定
   *
   * @param groupId グループID
   * @return ストリーミングの場合true
   */
  fun isGroupStreaming(groupId: Long): Boolean {
    return getGroupClientPacketCount(groupId) >= 2
  }

  /**
   * groupId が後から確定したリクエストを追跡対象に追加する。
   *
   * encoder.setGroupId() 後の遅延登録で使用するため、 groupRow と groupPacketCount の初期化を同一メソッドに集約する。
   */
  fun ensureGroupTracked(groupId: Long, rowIndex: Int) {
    if (!containsGroup(groupId)) {
      registerGroupRow(groupId, rowIndex)
    }
    if (getGroupPacketCount(groupId) == 0) {
      incrementGroupPacketCount(groupId)
    }
  }
}
