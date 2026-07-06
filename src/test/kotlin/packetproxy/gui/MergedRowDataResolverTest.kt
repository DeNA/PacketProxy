package packetproxy.gui

import javax.swing.JFrame
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class MergedRowDataResolverTest {
  @Test
  fun resolve_returnsRequestDataWhenNotMergedRow() {
    val requestData = byteArrayOf(1, 2, 3)
    val responseData = byteArrayOf(4, 5, 6)

    val result =
      MergedRowDataResolver.resolve(
        owner = mock(JFrame::class.java),
        message = "message",
        title = "title",
        isMergedRow = false,
        requestData = { requestData },
        responseData = { responseData },
      )

    assertThat(result).isEqualTo(requestData)
  }
}
