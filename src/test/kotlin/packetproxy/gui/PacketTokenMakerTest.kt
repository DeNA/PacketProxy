package packetproxy.gui

import javax.swing.text.Segment
import org.assertj.core.api.Assertions.assertThat
import org.fife.ui.rsyntaxtextarea.Token
import org.junit.jupiter.api.Test

class PacketTokenMakerTest {
  private data class Tok(val text: String, val type: Int)

  /** 複数行を順に字句解析し、行ごとのトークン列 (長さ0の内部トークンは除く) を返す */
  private fun tokenize(maker: PacketTokenMaker, text: String): List<List<Tok>> {
    var state = Token.NULL
    var offset = 0
    return text.split("\n").map { line ->
      val chars = line.toCharArray()
      var t: Token? = maker.getTokenList(Segment(chars, 0, chars.size), state, offset)
      val result = mutableListOf<Tok>()
      while (t != null) {
        if (t.length() > 0) result.add(Tok(t.lexeme, t.type))
        state = t.type
        t = t.nextToken
      }
      offset += line.length + 1
      result
    }
  }

  @Test
  fun detectStyle_httpWithJsonBody() {
    val text = "POST / HTTP/1.1\r\nHost: a\r\n\r\n{\"a\":1}"
    assertThat(PacketTokenMaker.detectStyle(text)).isEqualTo(PacketTokenMaker.STYLE_HTTP_JSON)
  }

  @Test
  fun detectStyle_jsonWithoutHeader() {
    assertThat(PacketTokenMaker.detectStyle("  [1, 2]\n")).isEqualTo(PacketTokenMaker.STYLE_JSON)
  }

  @Test
  fun detectStyle_serverSentEvents() {
    val text = "HTTP/1.1 200 OK\n\nevent: x\ndata: {\"a\":1}\n\n"
    assertThat(PacketTokenMaker.detectStyle(text)).isEqualTo(PacketTokenMaker.STYLE_HTTP_JSON)
  }

  @Test
  fun detectStyle_plainText() {
    assertThat(PacketTokenMaker.detectStyle("GET /?a=b HTTP/1.1\n\nhello"))
      .isEqualTo(PacketTokenMaker.STYLE_HTTP)
  }

  @Test
  fun detectStyle_largeTextWithoutSseIsLinear() {
    // 旧実装の正規表現 (?s).*data:\s*\{.*\}.* は後戻りで遅くなりうる入力
    val text = "data: x ".repeat(200_000)
    val start = System.nanoTime()
    assertThat(PacketTokenMaker.detectStyle(text)).isEqualTo(PacketTokenMaker.STYLE_HTTP)
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000)
  }

  @Test
  fun json_hugeSingleLineIsTokenizedInLinearTime() {
    // TokenMakerBase の置き場は 10 個ずつしか広がらず、この入力 (約 25 万トークン) で数秒かかっていた
    val line =
      (0 until 20_000).joinToString(",", "[", "]") { """{"id":$it,"name":"u$it","ok":true}""" }
    val maker = PacketTokenMaker.Json()
    val start = System.nanoTime()
    repeat(3) { tokenize(maker, line) }
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000)
  }

  @Test
  fun httpJson_headerLinesAreNotJsonColoredAndBodyIs() {
    val lines =
      tokenize(
        PacketTokenMaker.HttpJson(),
        "POST /?k=v HTTP/1.1\r\nX: \"q\"\r\n\r\n{\"key\": \"val\", \"n\": -1.5e3, \"b\": true, \"z\": null}",
      )

    assertThat(lines[0]).contains(Tok("k", Token.DATA_TYPE), Tok("v", Token.ANNOTATION))
    assertThat(lines[1].map { it.type }).doesNotContain(Token.LITERAL_STRING_DOUBLE_QUOTE)
    assertThat(lines[3])
      .contains(
        Tok("\"key\"", Token.VARIABLE),
        Tok("\"val\"", Token.LITERAL_STRING_DOUBLE_QUOTE),
        Tok("-1.5e3", Token.LITERAL_NUMBER_DECIMAL_INT),
        Tok("true", Token.LITERAL_BOOLEAN),
        Tok("null", Token.RESERVED_WORD_2),
        Tok("{", Token.SEPARATOR),
      )
  }

  @Test
  fun httpJson_blankFirstLineIsNotHeaderBodySeparator() {
    // detectStyle と同じく、先頭行の空行ではボディに切り替えない
    val lines = tokenize(PacketTokenMaker.HttpJson(), "\nX: \"q\"\n\n{\"k\": 1}")
    assertThat(lines[1].map { it.type }).doesNotContain(Token.LITERAL_STRING_DOUBLE_QUOTE)
    assertThat(lines[3]).contains(Tok("\"k\"", Token.VARIABLE))
  }

  @Test
  fun json_escapedQuoteStaysInsideString() {
    val lines = tokenize(PacketTokenMaker.Json(), """{"a\"b": "c\\"}""")
    assertThat(lines[0])
      .contains(
        Tok("\"a\\\"b\"", Token.VARIABLE),
        Tok("\"c\\\\\"", Token.LITERAL_STRING_DOUBLE_QUOTE),
      )
  }

  @Test
  fun keyValue_matchesSameSpansAsOldRegex() {
    val regex = Regex("([a-zA-Z0-9%.,/*_+-]+)=([a-zA-Z0-9%.,/*_+-]+)")
    val samples =
      listOf(
        "a=b&c=d",
        "a=b=c",
        "x==y",
        "ab= c",
        "=a",
        "a=",
        "k1=v1; k2 = v2, %41=%42",
        "GET /p?q=1&r=two#f HTTP/1.1",
        "Cookie: s=YWJj+/==; t=-1.5",
      )
    for (line in samples) {
      val expected =
        regex.findAll(line).flatMap { m ->
          listOf(Tok(m.groupValues[1], Token.DATA_TYPE), Tok(m.groupValues[2], Token.ANNOTATION))
        }
      val actual =
        tokenize(PacketTokenMaker.Http(), line)[0].filter {
          it.type == Token.DATA_TYPE || it.type == Token.ANNOTATION
        }
      assertThat(actual).describedAs(line).isEqualTo(expected.toList())
    }
  }

  @Test
  fun keyValue_longRunWithoutPairIsLinear() {
    // = のない長い base64 のような行。java.util.regex では二乗の時間がかかっていた
    val line = "A".repeat(200_000) + "=="
    val start = System.nanoTime()
    val tokens = tokenize(PacketTokenMaker.Http(), line)[0]
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000)
    assertThat(tokens.joinToString("") { it.text }).isEqualTo(line)
  }

  @Test
  fun http_bodyKeyValueIsColored() {
    val lines = tokenize(PacketTokenMaker.Http(), "POST / HTTP/1.1\r\n\r\na=b&c=d")
    assertThat(lines[2])
      .contains(
        Tok("a", Token.DATA_TYPE),
        Tok("b", Token.ANNOTATION),
        Tok("c", Token.DATA_TYPE),
        Tok("d", Token.ANNOTATION),
      )
  }

  @Test
  fun httpJson_serverSentEventsAreTokenizedPerLine() {
    val lines =
      tokenize(
        PacketTokenMaker.HttpJson(),
        "HTTP/1.1 200 OK\n\nevent: x\ndata: {\"a\":1}\n\ndata: {\"b\":true}",
      )
    assertThat(lines[3]).contains(Tok("\"a\"", Token.VARIABLE))
    assertThat(lines[5]).contains(Tok("\"b\"", Token.VARIABLE), Tok("true", Token.LITERAL_BOOLEAN))
  }

  @Test
  fun httpJson_blankLineInBodyKeepsBodyState() {
    // ボディ中の空行で、ヘッダの解析に戻らない
    val lines = tokenize(PacketTokenMaker.HttpJson(), "H: v\n\n{\"a\": 1,\n\n\"b\": 2}")
    assertThat(lines[4]).contains(Tok("\"b\"", Token.VARIABLE))
  }

  @Test
  fun tokensCoverWholeLine() {
    val line = "{\"a\": [1, \"x\"], é: ?}"
    val joined = tokenize(PacketTokenMaker.Json(), line)[0].joinToString("") { it.text }
    assertThat(joined).isEqualTo(line)
  }
}
