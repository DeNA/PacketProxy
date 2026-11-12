package packetproxy.util

import javax.swing.JTextArea
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import packetproxy.model.Database

class SearchBoxTest {
  companion object {
    /** SearchBox は FontManager 経由で設定 DB を読むので、一時ディレクトリに DB を開いておく */
    @JvmStatic
    @BeforeAll
    fun openDatabase() {
      val dir = java.nio.file.Files.createTempDirectory("searchbox-test")
      Database.getInstance().openAt(dir.resolve("test.sqlite3").toString())
    }
  }

  @Test
  fun manyMatchesAreCountedButHighlightedUpToLimit() {
    val area = JTextArea("a".repeat(100_000))
    val box = SearchBox()
    box.setBaseText(area)
    box.setText("a")

    assertThat(box.coloringSearchText()).isEqualTo(100_000)
    assertThat(area.highlighter.highlights).hasSize(SearchBox.MAX_HIGHLIGHTED_MATCHES)

    box.coloringClear()
    assertThat(area.highlighter.highlights).isEmpty()
  }
}
