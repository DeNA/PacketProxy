package packetproxy.gui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import packetproxy.model.Packet

class HttpPacketClipboardTest {
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
