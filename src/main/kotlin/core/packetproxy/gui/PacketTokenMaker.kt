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

import javax.swing.text.Segment
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.OccurrenceMarker
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenImpl
import org.fife.ui.rsyntaxtextarea.TokenMaker
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory

/**
 * パケット表示用の字句解析。RSyntaxTextArea は描画する行だけをこのクラスで解析するので、巨大なパケットでも全文に色付けする処理が走らない。
 *
 * 行をまたいで持ち越す状態は「HTTP ヘッダ部」か「ボディ部」かだけで、行末に長さ 0 の内部トークン（負の型）を置いて次の行へ渡す。
 *
 * RSyntaxTextArea 標準の TokenMakerBase はトークンの置き場を 10 個ずつしか広げないため、改行のない巨大な JSON のように 1 行に数十万トークンある
 * と行の解析が 2 乗の時間になる（1MB で数秒）。そのため TokenMaker を直接実装し、置き場を倍々で広げる。
 */
abstract class PacketTokenMaker : TokenMaker {
  companion object {
    /** JSON ではないテキスト。key=value 形式のパラメータだけ色付けする */
    const val STYLE_HTTP = "text/x-packetproxy-http"

    /** HTTP ヘッダ + JSON (または SSE) ボディ */
    const val STYLE_HTTP_JSON = "text/x-packetproxy-http-json"

    /** ヘッダなしの JSON (または SSE) */
    const val STYLE_JSON = "text/x-packetproxy-json"

    internal const val STATE_HEADER = -1
    internal const val STATE_BODY = -2

    private var registered = false

    @JvmStatic
    @Synchronized
    fun register() {
      if (registered) return
      val factory = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory
      val loader = PacketTokenMaker::class.java.classLoader
      factory.putMapping(STYLE_HTTP, Http::class.java.name, loader)
      factory.putMapping(STYLE_HTTP_JSON, HttpJson::class.java.name, loader)
      factory.putMapping(STYLE_JSON, Json::class.java.name, loader)
      registered = true
    }

    /** テキスト全体を見て、どの字句解析を使うかを決める。O(n) */
    @JvmStatic
    fun detectStyle(text: String): String {
      val bodyStart = findBodyStart(text)
      if (bodyStart >= 0) {
        val body = text.substring(bodyStart)
        if (looksLikeJson(body) || looksLikeServerSentEvents(body)) return STYLE_HTTP_JSON
      }
      if (looksLikeJson(text) || looksLikeServerSentEvents(text)) return STYLE_JSON
      return STYLE_HTTP
    }

    /** 最初の空行の直後の位置。空行がない、またはボディが空なら -1 */
    internal fun findBodyStart(text: String): Int {
      var lineStart = 0
      while (lineStart < text.length) {
        val nl = text.indexOf('\n', lineStart)
        if (nl < 0) return -1
        var lineEnd = nl
        if (lineEnd > lineStart && text[lineEnd - 1] == '\r') lineEnd--
        if (lineEnd == lineStart && lineStart > 0) return if (nl + 1 < text.length) nl + 1 else -1
        lineStart = nl + 1
      }
      return -1
    }

    internal fun looksLikeJson(text: String): Boolean {
      // Kotlin の trim() は Unicode の空白も削るので、Java の String.trim() と同じく制御文字と空白だけを削る
      val t = text.trim { it <= ' ' }
      return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
    }

    /** "data:" の後に "{" があり、その後に "}" があるか。正規表現の後戻りを避けて線形に調べる */
    internal fun looksLikeServerSentEvents(text: String): Boolean {
      var from = 0
      while (true) {
        val data = text.indexOf("data:", from)
        if (data < 0) return false
        var i = data + 5
        while (i < text.length && Character.isWhitespace(text[i])) i++
        if (i < text.length && text[i] == '{') return text.indexOf('}', i) >= 0
        from = data + 5
      }
    }

    private fun isFollowedByColon(a: CharArray, from: Int, end: Int): Boolean {
      var i = from
      while (i < end && Character.isWhitespace(a[i])) i++
      return i < end && a[i] == ':'
    }

    /** key=value の key と value に使える文字（英数字と % . , / * _ + -）。旧実装の正規表現の文字クラスと同じ */
    private fun isKeyValueChar(c: Char): Boolean =
      c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || "%.,/*_+-".indexOf(c) >= 0

    private fun isNumberChar(c: Char): Boolean =
      c in '0'..'9' || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-'
  }

  /** 再利用するトークンの置き場。getTokenList のたびに先頭から使い直す */
  private var pool = arrayOfNulls<TokenImpl>(0)
  private var used = 0
  private var firstToken: TokenImpl? = null
  private var currentToken: TokenImpl? = null

  /** 最初の行をボディとして扱うか */
  protected abstract fun startsInBody(): Boolean

  /** ボディを JSON として色付けするか */
  protected abstract fun jsonBody(): Boolean

  private fun resetTokenList() {
    firstToken = null
    currentToken = null
    used = 0
  }

  private fun append(token: TokenImpl) {
    if (firstToken == null) firstToken = token else currentToken!!.setNextToken(token)
    currentToken = token
  }

  override fun addToken(array: CharArray, start: Int, end: Int, tokenType: Int, startOffset: Int) {
    if (used == pool.size) {
      val grown = pool.copyOf(maxOf(64, pool.size * 2))
      for (i in pool.size until grown.size) grown[i] = TokenImpl()
      pool = grown
    }
    val token = pool[used++]!!
    token.set(array, start, end, startOffset, tokenType) // 次のトークンへのリンクもここで消える
    token.setHyperlink(false)
    token.setLanguageIndex(0)
    append(token)
  }

  override fun addNullToken() {
    append(TokenImpl())
  }

  /** 出現箇所のハイライト (setMarkOccurrences) は RawTextPane で無効にしているので使われない */
  override fun getOccurrenceMarker(): OccurrenceMarker? = null

  override fun getTokenList(text: Segment, initialTokenType: Int, startOffset: Int): Token {
    resetTokenList()
    val firstLine = initialTokenType != STATE_HEADER && initialTokenType != STATE_BODY
    var state =
      when {
        !firstLine -> initialTokenType
        startsInBody() -> STATE_BODY
        else -> STATE_HEADER
      }

    val array = text.array
    val start = text.offset
    val end = text.offset + text.count

    if (state == STATE_HEADER) {
      // detectStyle (findBodyStart) と同じく、先頭行の空行はヘッダとボディの区切りとみなさない
      val blank = text.count == 0 || (text.count == 1 && array[start] == '\r')
      if (blank && !firstLine) {
        add(array, start, end, Token.WHITESPACE, text, startOffset)
        state = STATE_BODY
      } else {
        tokenizeKeyValue(text, startOffset)
      }
    } else if (jsonBody()) {
      tokenizeJson(text, startOffset)
    } else {
      tokenizeKeyValue(text, startOffset)
    }

    // 行末に長さ 0 の内部トークンを置き、次の行へ状態を渡す（負の型は描画されない）
    addToken(array, end, end - 1, state, startOffset + text.count)
    return firstToken!!
  }

  /** [from, to) を tokenType のトークンとして追加する */
  private fun add(
    array: CharArray,
    from: Int,
    to: Int,
    tokenType: Int,
    text: Segment,
    startOffset: Int,
  ) {
    if (to <= from) return
    addToken(array, from, to - 1, tokenType, startOffset + (from - text.offset))
  }

  /**
   * key=value の key と value を色付けする。正規表現 ([文字]+)=([文字]+) と同じ箇所を、後戻りせずに 1 回の走査で見つける。
   *
   * java.util.regex だと、= のない長い base64 のような行で二乗の時間がかかる。
   */
  private fun tokenizeKeyValue(text: Segment, startOffset: Int) {
    val a = text.array
    val end = text.offset + text.count
    var plainStart = text.offset
    var i = text.offset
    while (i < end) {
      if (!isKeyValueChar(a[i])) {
        i++
        continue
      }
      val keyStart = i
      while (i < end && isKeyValueChar(a[i])) i++
      val keyEnd = i
      if (i + 1 < end && a[i] == '=' && isKeyValueChar(a[i + 1])) {
        val valueStart = i + 1
        i = valueStart
        while (i < end && isKeyValueChar(a[i])) i++
        add(a, plainStart, keyStart, Token.IDENTIFIER, text, startOffset)
        add(a, keyStart, keyEnd, Token.DATA_TYPE, text, startOffset)
        add(a, keyEnd, valueStart, Token.IDENTIFIER, text, startOffset)
        add(a, valueStart, i, Token.ANNOTATION, text, startOffset)
        plainStart = i
      }
    }
    add(a, plainStart, end, Token.IDENTIFIER, text, startOffset)
  }

  private fun tokenizeJson(text: Segment, startOffset: Int) {
    val a = text.array
    val end = text.offset + text.count
    var i = text.offset
    while (i < end) {
      val c = a[i]
      var j = i + 1
      val type: Int
      if (Character.isWhitespace(c)) {
        while (j < end && Character.isWhitespace(a[j])) j++
        type = Token.WHITESPACE
      } else if (c == '"') {
        while (j < end && a[j] != '"') j += if (a[j] == '\\') 2 else 1
        j = minOf(j + 1, end)
        type =
          if (isFollowedByColon(a, j, end)) Token.VARIABLE else Token.LITERAL_STRING_DOUBLE_QUOTE
      } else if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',') {
        type = Token.SEPARATOR
      } else if (c == '-' || c in '0'..'9') {
        while (j < end && isNumberChar(a[j])) j++
        type = Token.LITERAL_NUMBER_DECIMAL_INT
      } else if (Character.isLetter(c)) {
        while (j < end && Character.isLetterOrDigit(a[j])) j++
        type =
          when (String(a, i, j - i)) {
            "true",
            "false" -> Token.LITERAL_BOOLEAN
            "null" -> Token.RESERVED_WORD_2
            else -> Token.IDENTIFIER
          }
      } else {
        type = Token.IDENTIFIER
      }
      add(a, i, j, type, text, startOffset)
      i = j
    }
  }

  class Http : PacketTokenMaker() {
    override fun startsInBody() = false

    override fun jsonBody() = false
  }

  class HttpJson : PacketTokenMaker() {
    override fun startsInBody() = false

    override fun jsonBody() = true
  }

  class Json : PacketTokenMaker() {
    override fun startsInBody() = true

    override fun jsonBody() = true
  }
}
