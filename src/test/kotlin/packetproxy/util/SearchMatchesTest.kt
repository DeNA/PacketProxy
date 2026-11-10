package packetproxy.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SearchMatchesTest {
  private fun starts(m: SearchMatches) = (0 until m.size).map { m.start(it) }

  @Test
  fun find_countsNonOverlappingMatches() {
    assertThat(starts(SearchMatches.find("aaaa", "aa"))).containsExactly(0, 2)
    assertThat(starts(SearchMatches.find("xabxab", "ab"))).containsExactly(1, 4)
    assertThat(SearchMatches.find("abc", "").size).isZero()
    assertThat(SearchMatches.find("", "a").size).isZero()
  }

  @Test
  fun find_manyMatchesIsLinear() {
    val start = System.nanoTime()
    val m = SearchMatches.find("a".repeat(1_000_000), "a")
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000)
    assertThat(m.size).isEqualTo(1_000_000)
  }

  @Test
  fun indicesOverlapping_includesMatchesCutByTheRange() {
    // 一致の開始位置: 0, 10, 20, 30 (長さ 3)
    val m =
      SearchMatches.find("abc".padEnd(10) + "abc".padEnd(10) + "abc".padEnd(10) + "abc", "abc")
    assertThat(m.indicesOverlapping(0, 40).toList()).containsExactly(0, 1, 2, 3)
    // 11 から始まる範囲は、10 から始まる一致の途中を含む
    assertThat(m.indicesOverlapping(11, 21).toList()).containsExactly(1, 2)
    assertThat(m.indicesOverlapping(13, 20).toList()).isEmpty()
    assertThat(m.indicesOverlapping(5, 5).toList()).isEmpty()
  }

  @Test
  fun nextAndPreviousWrapAround() {
    val m = SearchMatches.find("-a--a---a", "a") // 1, 4, 8
    assertThat(m.nextFrom(0)).isEqualTo(0)
    assertThat(m.nextFrom(1)).isEqualTo(0)
    assertThat(m.nextFrom(2)).isEqualTo(1)
    assertThat(m.nextFrom(9)).isEqualTo(0)
    assertThat(m.previousBefore(4)).isEqualTo(0)
    assertThat(m.previousBefore(5)).isEqualTo(1)
    assertThat(m.previousBefore(1)).isEqualTo(2)
    assertThat(SearchMatches.EMPTY.nextFrom(0)).isEqualTo(-1)
    assertThat(SearchMatches.EMPTY.previousBefore(0)).isEqualTo(-1)
  }
}
