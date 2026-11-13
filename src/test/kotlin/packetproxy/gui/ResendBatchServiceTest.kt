package packetproxy.gui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import packetproxy.model.Packet

class ResendBatchServiceTest {
  private lateinit var service: ResendBatchService

  @BeforeEach
  fun setUp() {
    service = ResendBatchService.getInstance()
    service.clear()
  }

  @Test
  fun registerPendingBatch_andRegisterPacket_setsRepresentative() {
    service.registerPendingBatch(100L, 10, 20)
    service.registerPacket(100L, 201, 10)

    assertThat(service.isRepresentativePacket(201)).isTrue()
    assertThat(service.isCollapsed(100L)).isTrue()
    assertThat(service.getCompactCountLabel(100L)).isEqualTo("×1/20")
    assertThat(service.getSummaryTooltip(100L)).isEqualTo("Resent x1/20 from #10")
  }

  @Test
  fun shouldHidePacket_hidesNonRepresentativeRowsWhenCollapsed() {
    service.registerPendingBatch(200L, 11, 20)
    service.registerPacket(200L, 301, 11)
    service.registerPacket(200L, 302, 11)

    assertThat(service.shouldHidePacket(301)).isFalse()
    assertThat(service.shouldHidePacket(302)).isTrue()
  }

  @Test
  fun toggleCollapsed_showsAllBatchRowsWhenExpanded() {
    service.registerPendingBatch(300L, 12, 20)
    service.registerPacket(300L, 401, 12)
    service.registerPacket(300L, 402, 12)

    service.toggleCollapsed(300L)

    assertThat(service.isCollapsed(300L)).isFalse()
    assertThat(service.shouldHidePacket(402)).isFalse()
    assertThat(service.getCompactCountLabel(300L)).isEqualTo("×2/20")
    assertThat(service.getSummaryTooltip(300L)).isEqualTo("Resent x2/20 from #12")
  }

  @Test
  fun expandAll_andCollapseAll_updateBatchStates() {
    service.registerPendingBatch(400L, 13, 20)
    service.registerPacket(400L, 501, 13)

    service.expandAll()
    assertThat(service.areAllExpanded()).isTrue()

    service.collapseAll()
    assertThat(service.isCollapsed(400L)).isTrue()
  }

  @Test
  fun clear_resetsBatchState() {
    service.registerPendingBatch(500L, 14, 20)
    service.registerPacket(500L, 601, 14)

    service.clear()

    assertThat(service.getBatchIdForPacket(601)).isEqualTo(0L)
    assertThat(service.shouldHidePacket(601)).isFalse()
  }

  @Test
  fun rebuildFromPackets_restoresBatchMembership() {
    service.rebuildFromPackets(
      listOf(701, 702, 703),
      listOf(600L, 600L, 0L),
      listOf(15, 15, 0),
      listOf(Packet.Direction.CLIENT, Packet.Direction.CLIENT, Packet.Direction.SERVER),
    )

    assertThat(service.getBatchIdForPacket(701)).isEqualTo(600L)
    assertThat(service.getBatchIdForPacket(702)).isEqualTo(600L)
    assertThat(service.getBatchIdForPacket(703)).isEqualTo(0L)
    assertThat(service.isRepresentativePacket(701)).isTrue()
    assertThat(service.shouldHidePacket(702)).isTrue()
  }
}
