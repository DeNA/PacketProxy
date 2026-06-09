/*
 * Copyright 2025 DeNA Co., Ltd.
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
package packetproxy.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import core.packetproxy.util.readUtf8Line
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.jline.jansi.Ansi
import org.jline.jansi.Ansi.Color.RED
import org.slf4j.LoggerFactory
import packetproxy.gui.GUILog

/** ログの出力先モード */
enum class LogMode {
  /** GUIモード: stdout に出力し、GUIログにも追記する (既存の isGulp=false) */
  GUI,

  /** gulp REPL モード: ファイル (logs/gulp.log) に出力する (既存の isGulp=true) */
  GULP_FILE,

  /** server CLI モード: stderr に出力する (CI でログを可視化) */
  SERVER_STDERR,

  /** encode/decode CLI モード: 出力を抑制して stdout を汚さない */
  SILENT,
}

object Logging {
  private val dtf: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
  private val guiLog: GUILog = GUILog.getInstance()
  private val logger = LoggerFactory.getLogger("")
  private var isGulp: Boolean = false

  // log出力先のファイルの絶対PATH
  private val logFilePath by lazy {
    val projectRoot = System.getProperty("app.home") ?: "."
    val logDir = File(projectRoot, "logs")
    if (!logDir.exists()) logDir.mkdirs()
    "${logDir.absolutePath}/gulp.log"
  }

  private val logFile by lazy {
    val logFile = File(logFilePath)
    if (!logFile.exists()) throw IOException("not found: ${logFile.absolutePath}")
    logFile
  }

  /** 後方互換: isGulp=true → GULP_FILE, isGulp=false → GUI */
  @JvmStatic fun init(isGulp: Boolean) = init(if (isGulp) LogMode.GULP_FILE else LogMode.GUI)

  @JvmStatic
  fun init(mode: LogMode) {
    isGulp = (mode != LogMode.GUI)
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    context.reset()

    val rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)

    if (mode == LogMode.SILENT) {
      rootLogger.level = Level.OFF
      return
    }

    val encoder =
      PatternLayoutEncoder().apply {
        this.context = context
        pattern = "%msg%n"
        start()
      }

    val appender =
      when (mode) {
        LogMode.GULP_FILE ->
          FileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "FILE"
            file = logFilePath
            isAppend = false
            this.encoder = encoder
            start()
          }
        LogMode.SERVER_STDERR ->
          ConsoleAppender<ILoggingEvent>().apply {
            this.context = context
            name = "STDERR"
            target = "System.err"
            this.encoder = encoder
            start()
          }
        else ->
          ConsoleAppender<ILoggingEvent>().apply {
            this.context = context
            name = "CONSOLE"
            this.encoder = encoder
            start()
          }
      }

    rootLogger.addAppender(appender)
    // ormliteなどのdebugログを抑制するため、WARN未満は出力しない
    rootLogger.level = Level.WARN
  }

  @JvmStatic
  @Throws(IllegalFormatException::class)
  fun log(format: Any, vararg args: Any?) {
    val fs = formatString(format, *args)

    // WARN未満は出力されないためwarnで出力する
    logger.warn(fs)
    if (isGulp) return
    guiLog.append(fs)
  }

  @JvmStatic
  @Throws(IllegalFormatException::class)
  fun err(format: Any, vararg args: Any?) {
    val fs = formatString(format, *args)

    logger.error(Ansi.ansi().fg(RED).a(fs).reset().toString())
    if (isGulp) return
    guiLog.appendErr(fs)
  }

  /** 別のログが挟まらないように一塊にした上で１度に出力する */
  @JvmStatic
  @Throws(IllegalFormatException::class)
  fun errWithStackTrace(e: Throwable) {
    val sb = StringBuilder()
    sb.append(e.toString())

    for (element in e.stackTrace) {
      sb.append("\n$element")
    }
    err(sb.toString())
  }

  /** logの継続出力を行う */
  suspend fun tailLog() {
    RandomAccessFile(logFile, "r").use { raf ->
      // ブロッキング実行される場合は先頭、そうでない場合は末尾30行目から出力を開始する
      raf.seek(0)

      // 新しい追加分を追跡する
      while (true) {
        yield()
        printRemaining(raf)
        delay(100)
      }
    }
  }

  /** raf.seekされた箇所から末尾までを出力する */
  private fun printRemaining(raf: RandomAccessFile) {
    val initialLength = logFile.length()
    while (raf.filePointer < initialLength) {
      println(raf.readUtf8Line())
    }
  }

  /** 第１引数が文字列でないなどの場合はtoString()を実行する 第１引数が文字列かつ第２引数移行が正しく指定されている場合のみフォーマット指定子としての解釈を行う */
  private fun formatString(format: Any, vararg args: Any?): String {
    val dateTime = dtf.format(LocalDateTime.now()) + "     "
    val indent = " ".repeat(dateTime.length)

    val msg =
      if (format is String && args.isNotEmpty()) {
        try {
          format.format(*args)
        } catch (e: Exception) {
          format
        }
      } else {
        format.toString()
      }

    return dateTime + msg.replace("\n", "\n$indent")
  }
}
