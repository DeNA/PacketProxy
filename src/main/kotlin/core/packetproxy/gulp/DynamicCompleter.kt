package core.packetproxy.gulp

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import packetproxy.cli.CLIModeHandler

/** 動的にモードに応じて補完を変更するCompleter */
class DynamicCompleter(private var handler: CLIModeHandler) : Completer {
  fun updateHandler(newHandler: CLIModeHandler) {
    handler = newHandler
  }

  override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
    handler.completer.complete(reader, line, candidates)
  }
}
