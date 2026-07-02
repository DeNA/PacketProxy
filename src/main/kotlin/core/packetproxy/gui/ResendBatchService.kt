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
 * 一括再送バッチの折りたたみ状態を管理するサービス。
 *
 * GUIHistory から EDT 上でのみ呼び出される前提のため、同期コレクションは使用しない。
 */
class ResendBatchService private constructor() {
  private data class BatchInfo(
    val sourceId: Int,
    val expectedCount: Int,
    var representativePacketId: Int = 0,
    val requestPacketIds: MutableList<Int> = mutableListOf(),
    var collapsed: Boolean = true,
  )

  private val batches: MutableMap<Long, BatchInfo> = HashMap()
  private val packetToBatchId: MutableMap<Int, Long> = HashMap()

  companion object {
    @Volatile private var instance: ResendBatchService? = null

    @JvmStatic
    fun getInstance(): ResendBatchService {
      return instance
        ?: synchronized(this) { instance ?: ResendBatchService().also { instance = it } }
    }
  }

  fun clear() {
    batches.clear()
    packetToBatchId.clear()
  }

  fun registerPendingBatch(batchId: Long, sourceId: Int, expectedCount: Int) {
    if (batchId == 0L || expectedCount <= 1) {
      return
    }
    batches.compute(batchId) { _, existing ->
      existing ?: BatchInfo(sourceId = sourceId, expectedCount = expectedCount)
    }
  }

  fun registerPacket(batchId: Long, requestPacketId: Int, sourceId: Int) {
    if (batchId == 0L) {
      return
    }
    val batch =
      batches.compute(batchId) { _, existing ->
        existing ?: BatchInfo(sourceId = sourceId, expectedCount = 0)
      } ?: return

    if (!batch.requestPacketIds.contains(requestPacketId)) {
      batch.requestPacketIds.add(requestPacketId)
    }
    if (batch.representativePacketId == 0) {
      batch.representativePacketId = requestPacketId
    }
    packetToBatchId[requestPacketId] = batchId
  }

  fun getBatchIdForPacket(packetId: Int): Long {
    return packetToBatchId[packetId] ?: 0L
  }

  fun isRepresentativePacket(packetId: Int): Boolean {
    val batchId = getBatchIdForPacket(packetId)
    if (batchId == 0L) {
      return false
    }
    return batches[batchId]?.representativePacketId == packetId
  }

  fun isCollapsed(batchId: Long): Boolean {
    return batches[batchId]?.collapsed ?: true
  }

  fun toggleCollapsed(batchId: Long) {
    val batch = batches[batchId] ?: return
    batch.collapsed = !batch.collapsed
  }

  fun expandAll() {
    batches.values.forEach { batch -> batch.collapsed = false }
  }

  fun collapseAll() {
    batches.values.forEach { batch -> batch.collapsed = true }
  }

  fun areAllExpanded(): Boolean {
    if (batches.isEmpty()) {
      return true
    }
    return batches.values.all { batch -> !batch.collapsed }
  }

  fun shouldHidePacket(packetId: Int): Boolean {
    val batchId = getBatchIdForPacket(packetId)
    if (batchId == 0L) {
      return false
    }
    val batch = batches[batchId] ?: return false
    if (!batch.collapsed) {
      return false
    }
    return batch.representativePacketId != packetId
  }

  fun getSummaryPrefix(batchId: Long): String {
    return if (isCollapsed(batchId)) "[+]" else "[-]"
  }

  fun getSummaryLabel(batchId: Long): String {
    val batch = batches[batchId] ?: return ""
    val receivedCount = batch.requestPacketIds.size
    val countLabel =
      if (batch.expectedCount > 0) {
        "$receivedCount/${batch.expectedCount}"
      } else {
        "$receivedCount"
      }
    val sourceLabel =
      if (batch.sourceId > 0) {
        " from #${batch.sourceId}"
      } else {
        ""
      }
    return "${getSummaryPrefix(batchId)} Resent x$countLabel$sourceLabel"
  }

  fun rebuildFromPackets(
    packetIds: List<Int>,
    batchIds: List<Long>,
    sourceIds: List<Int>,
    directions: List<packetproxy.model.Packet.Direction>,
  ) {
    clear()
    for (i in packetIds.indices) {
      if (batchIds[i] == 0L) {
        continue
      }
      if (directions[i] != packetproxy.model.Packet.Direction.CLIENT) {
        continue
      }
      registerPacket(batchIds[i], packetIds[i], sourceIds[i])
    }
  }
}
