package packetproxy.gui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import packetproxy.model.Packet
import packetproxy.util.CharSetUtility

class HttpPacketClipboardTest {
  @BeforeEach
  fun setUp() {
    val utility = CharSetUtility.getInstance()
    val charSetField = CharSetUtility::class.java.getDeclaredField("charSet")
    charSetField.isAccessible = true
    charSetField.set(utility, "UTF-8")
    val isAutoField = CharSetUtility::class.java.getDeclaredField("isAuto")
    isAutoField.isAccessible = true
    isAutoField.setBoolean(utility, false)
  }

  @Test
  fun formatMethodUrlBody_returnsTabSeparatedMethodUrlAndBody() {
    val data =
      """
      GET /hello HTTP/1.1
      Host: example.com

      request-body
      """
        .trimIndent()
        .replace("\n", "\r\n")
        .toByteArray()
    val packet = mock(Packet::class.java)
    `when`(packet.serverPort).thenReturn(443)
    `when`(packet.useSSL).thenReturn(true)

    val result = formatMethodUrlBody(data, packet)

    assertThat(result).isEqualTo("GET\thttps://example.com/hello\trequest-body")
  }
}
