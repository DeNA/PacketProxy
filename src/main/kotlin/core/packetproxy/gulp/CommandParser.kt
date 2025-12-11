package core.packetproxy.gulp

/** cmdは常に両端が空白でない１文字以上の文字列 */
data class ParsedCommand(val raw: String, val cmd: String, val args: List<String>) {
  /** argsの先頭を新たなcmdとしたParsedCommandを返す argsはemptyな可能性あり */
  fun shift(): ParsedCommand? {
    val (cmd, args) = CommandParser.shift(args) ?: return null
    return ParsedCommand(raw, cmd, args)
  }

  //    fun hasOption(opt: String): Boolean = args.contains(opt)
}

object CommandParser {
  // [^\\s"]+  -> クォートでもスペースでもない文字の塊（普通の単語）
  // "([^"]*)" -> ダブルクォートで囲まれた中身（スペースを含める）
  private val regex = Regex("""[^\s"]+|"([^"]*)"""")

  fun parse(line: String): ParsedCommand? {
    // マッチした部分を全てリストへ
    val tokens =
      regex
        .findAll(line.trim())
        .map { matchResult ->
          // クォートの中身(groupValues[1])があればそれを、なければ全体(groupValues[0])を使う
          matchResult.groupValues[1].ifEmpty { matchResult.groupValues[0] }
        }
        .toList()

    // 先頭をコマンド、残りを引数リストとして返す
    // tokensが全て空文字だった場合はnullを返す
    val (cmd, args) = shift(tokens) ?: return null
    return ParsedCommand(line, cmd, args)
  }

  fun shift(args: List<String>): Pair<String, List<String>>? {
    val cleaned = args.dropWhile { it.isEmpty() }
    if (cleaned.isEmpty()) return null
    return Pair(cleaned.first().trim(), cleaned.drop(1))
  }
}
