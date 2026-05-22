package packetproxy.gui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PacketPairingServiceTest {
  @Test
  fun registerPairing_registersBidirectionalMappings() {
    // registerPairing() を呼ぶと、レスポンス→リクエスト・リクエスト→レスポンスの双方向マッピングが登録され、
    // リクエスト側のパケットが「マージ済み行」として扱われること
    val service = PacketPairingService()

    val requestPacketId = 100
    val responsePacketId = 200
    service.registerPairing(responsePacketId, requestPacketId)

    assertThat(service.getRequestIdForResponse(responsePacketId)).isEqualTo(requestPacketId)
    assertThat(service.getResponsePacketIdForRequest(requestPacketId)).isEqualTo(responsePacketId)
    assertThat(service.containsResponsePairing(responsePacketId)).isTrue()
    assertThat(service.isMergedRow(requestPacketId)).isTrue()
    assertThat(service.isMergedRow(responsePacketId)).isFalse()
  }

  @Test
  fun unregisterPairingByRequestId_removesMappingsAndReturnsResponseId() {
    // unregisterPairingByRequestId() を呼ぶと、双方向マッピングがすべて削除され、
    // 削除前のレスポンスIDが返り値として得られること
    val service = PacketPairingService()

    val requestPacketId = 101
    val responsePacketId = 201
    service.registerPairing(responsePacketId, requestPacketId)

    val removedResponsePacketId = service.unregisterPairingByRequestId(requestPacketId)

    assertThat(removedResponsePacketId).isEqualTo(responsePacketId)
    assertThat(service.containsResponsePairing(responsePacketId)).isFalse()
    assertThat(service.getRequestIdForResponse(responsePacketId)).isEqualTo(-1)
    assertThat(service.getResponsePacketIdForRequest(requestPacketId)).isEqualTo(-1)
    assertThat(service.isMergedRow(requestPacketId)).isFalse()
  }

  @Test
  fun groupPacketCount_andMergeableBoundary() {
    // グループ内のパケット数が2以下ならマージ可能、3以上になるとマージ不可になること
    // （3パケット目はgRPCストリーミングなど複数レスポンスを持つストリーミング通信の可能性があるため）
    val service = PacketPairingService()
    val groupId = 1L

    assertThat(service.incrementGroupPacketCount(groupId)).isEqualTo(1)
    assertThat(service.isGroupMergeable(groupId)).isTrue()

    assertThat(service.incrementGroupPacketCount(groupId)).isEqualTo(2)
    assertThat(service.isGroupMergeable(groupId)).isTrue()

    assertThat(service.incrementGroupPacketCount(groupId)).isEqualTo(3)
    assertThat(service.getGroupPacketCount(groupId)).isEqualTo(3)
    assertThat(service.isGroupMergeable(groupId)).isFalse()
  }

  @Test
  fun twoClientPacketsInSameGroup_notMergeable() {
    val service = PacketPairingService()
    val groupId = 9L

    // 1つ目のCLIENTパケット：まだマージ可能
    service.incrementGroupPacketCount(groupId)
    service.incrementGroupClientPacketCount(groupId)
    assertThat(service.isGroupMergeable(groupId)).isTrue()

    // 2つ目のCLIENTパケット：同一グループに2つのRequestが存在 → ストリーミング扱い、マージ不可
    service.incrementGroupPacketCount(groupId)
    service.incrementGroupClientPacketCount(groupId)
    assertThat(service.getGroupPacketCount(groupId)).isEqualTo(2)
    assertThat(service.getGroupClientPacketCount(groupId)).isEqualTo(2)
    assertThat(service.isGroupMergeable(groupId)).isFalse()
    assertThat(service.isGroupStreaming(groupId)).isTrue()
  }

  @Test
  fun groupClientPacketCount_andStreamingBoundary() {
    // 同一グループでCLIENTパケット数が1ならストリーミングではなく、2以上になるとストリーミング扱いになること
    // gRPC-Streamingのエンコーダを使用した場合、HEADERSフレームとDATAフレームで2つのCLIENTパケットが存在するため
    val service = PacketPairingService()
    val groupId = 2L

    assertThat(service.incrementGroupClientPacketCount(groupId)).isEqualTo(1)
    assertThat(service.isGroupStreaming(groupId)).isFalse()

    assertThat(service.incrementGroupClientPacketCount(groupId)).isEqualTo(2)
    assertThat(service.getGroupClientPacketCount(groupId)).isEqualTo(2)
    assertThat(service.isGroupStreaming(groupId)).isTrue()
  }

  @Test
  fun clear_resetsAllState() {
    // clear() を呼ぶと、グループ行マッピング・レスポンス有無フラグ・パケットペアリング・
    // パケットカウントなどすべての内部状態が初期値にリセットされること
    val service = PacketPairingService()

    service.registerGroupRow(10L, 3)
    service.markGroupHasResponse(10L)
    service.registerPairing(210, 110)
    service.incrementGroupPacketCount(10L)
    service.incrementGroupClientPacketCount(10L)

    service.clear()

    assertThat(service.getRowForGroup(10L)).isNull()
    assertThat(service.hasResponse(10L)).isFalse()
    assertThat(service.containsResponsePairing(210)).isFalse()
    assertThat(service.getGroupPacketCount(10L)).isEqualTo(0)
    assertThat(service.getGroupClientPacketCount(10L)).isEqualTo(0)
    assertThat(service.isGroupStreaming(10L)).isFalse()
  }

  @Test
  fun unmergeGroup_onlyClearsHasResponse() {
    // unmergeGroup() は「レスポンス受信済み」フラグのみをクリアし、
    // パケットIDのペアリングマッピングは unregisterPairingByRequestId() が別途呼ばれるまで保持されること
    val service = PacketPairingService()
    val groupId = 5L

    service.markGroupHasResponse(groupId)
    service.registerPairing(responsePacketId = 301, requestPacketId = 201)

    service.unmergeGroup(groupId)

    assertThat(service.hasResponse(groupId)).isFalse()
    // ペアリングIDのマッピングは unregisterPairingByRequestId() が明示的に呼ばれるまで保持される
    assertThat(service.getResponsePacketIdForRequest(201)).isEqualTo(301)
    assertThat(service.getRequestIdForResponse(301)).isEqualTo(201)
  }

  @Test
  fun isGroupMergeable_normalHttpPair_returnsTrue() {
    // 通常のHTTP通信（CLIENTが1つ、SERVERが1つ）はマージ可能
    val service = PacketPairingService()
    val groupId = 20L

    service.incrementGroupPacketCount(groupId) // CLIENT
    service.incrementGroupClientPacketCount(groupId)
    service.incrementGroupPacketCount(groupId) // SERVER

    assertThat(service.getGroupPacketCount(groupId)).isEqualTo(2)
    assertThat(service.getGroupClientPacketCount(groupId)).isEqualTo(1)
    assertThat(service.isGroupMergeable(groupId)).isTrue()
    assertThat(service.isGroupStreaming(groupId)).isFalse()
  }

  @Test
  fun getters_returnDefaultValues_whenGroupNotRegistered() {
    // 未登録のgroupIdに対して各getterがデフォルト値を返すこと
    val service = PacketPairingService()
    val unknownGroupId = 999L

    assertThat(service.getRowForGroup(unknownGroupId)).isNull()
    assertThat(service.containsGroup(unknownGroupId)).isFalse()
    assertThat(service.hasResponse(unknownGroupId)).isFalse()
    assertThat(service.getGroupPacketCount(unknownGroupId)).isEqualTo(0)
    assertThat(service.getGroupClientPacketCount(unknownGroupId)).isEqualTo(0)
    assertThat(service.isGroupMergeable(unknownGroupId)).isTrue()
    assertThat(service.isGroupStreaming(unknownGroupId)).isFalse()
  }

  @Test
  fun getters_returnDefaultValues_whenPacketNotPaired() {
    // ペアリング未登録のパケットIDに対して各getterがデフォルト値を返すこと
    val service = PacketPairingService()
    val unknownPacketId = 999

    assertThat(service.getRequestIdForResponse(unknownPacketId)).isEqualTo(-1)
    assertThat(service.getResponsePacketIdForRequest(unknownPacketId)).isEqualTo(-1)
    assertThat(service.containsResponsePairing(unknownPacketId)).isFalse()
    assertThat(service.isMergedRow(unknownPacketId)).isFalse()
  }

  @Test
  fun unregisterPairingByRequestId_returnsMinusOne_whenNotPaired() {
    // ペアリングが存在しないリクエストIDに対して -1 が返ること
    val service = PacketPairingService()

    val result = service.unregisterPairingByRequestId(999)

    assertThat(result).isEqualTo(-1)
  }

  @Test
  fun ensureGroupTracked_registersGroupAndCount_whenNotYetTracked() {
    // 未登録のgroupIdに対して呼ぶと行番号とカウントが初期化される
    val service = PacketPairingService()
    val groupId = 30L
    val rowIndex = 5

    service.ensureGroupTracked(groupId, rowIndex)

    assertThat(service.containsGroup(groupId)).isTrue()
    assertThat(service.getRowForGroup(groupId)).isEqualTo(rowIndex)
    assertThat(service.getGroupPacketCount(groupId)).isEqualTo(1)
  }

  @Test
  fun ensureGroupTracked_isIdempotent_whenCalledTwice() {
    // 同じgroupIdで2回呼んでも行番号・カウントが重複登録されないこと
    val service = PacketPairingService()
    val groupId = 31L
    val rowIndex = 7

    service.ensureGroupTracked(groupId, rowIndex)
    service.ensureGroupTracked(groupId, rowIndex)

    assertThat(service.getRowForGroup(groupId)).isEqualTo(rowIndex)
    assertThat(service.getGroupPacketCount(groupId)).isEqualTo(1)
  }
}
