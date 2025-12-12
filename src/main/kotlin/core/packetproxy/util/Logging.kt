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
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import packetproxy.gui.GUILog

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

  private var tailThread: Thread? = null
  private val keepTailing = AtomicBoolean(true) // CLI modeでのログ出力継続を管理するフラグ

  private fun formatString(format: String, vararg args: Any?): String {
    val now = LocalDateTime.now()
    return dtf.format(now) + "       " + String.format(format, *args)
  }

  @JvmStatic
  fun init(isGulp: Boolean) {
    this.isGulp = isGulp
    val context = LoggerFactory.getILoggerFactory() as LoggerContext

    context.reset()

    val encoder =
      PatternLayoutEncoder().apply {
        this.context = context
        pattern = "%msg%n"
        start()
      }

    val appender =
      if (isGulp) {
        FileAppender<ILoggingEvent>().apply {
          this.context = context
          name = "FILE"
          file = logFilePath
          isAppend = false
          this.encoder = encoder
          start()
        }
      } else {
        ConsoleAppender<ILoggingEvent>().apply {
          this.context = context
          name = "CONSOLE"
          this.encoder = encoder
          start()
        }
      }

    val rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    rootLogger.addAppender(appender)
    rootLogger.level = Level.INFO
  }

  @JvmStatic
  @Throws(IllegalFormatException::class)
  fun log(format: String, vararg args: Any?) {
    val fs = formatString(format, *args)

    logger.info(fs)
    if (!isGulp) guiLog.append(fs)
  }

  @JvmStatic
  @Throws(IllegalFormatException::class)
  fun err(format: String, vararg args: Any?) {
    val fs = formatString(format, *args)

    logger.error(fs)
    if (!isGulp) guiLog.appendErr(fs)
  }

  @JvmStatic
  @Throws(IllegalFormatException::class)
  fun errWithStackTrace(e: Throwable) {
    err(e.toString())
    val stackTrace = e.getStackTrace()
    for (element in stackTrace) {
      err(element.toString())
    }
  }

  /**
   * CLIモードでのlogの継続出力を行う
   *
   * @param blocking trueの場合、メインスレッドでブロッキング実行し、ファイルの先頭から継続出力する（Ctrl+Cで停止可能）
   *   falseの場合、別スレッドで非ブロッキング実行し、ファイルの末尾から継続出力する（デフォルト）
   */
  @JvmStatic
  @JvmOverloads
  fun startTailLog(blocking: Boolean = false) {
    if (tailThread != null || !isGulp) return

    val tailLogRunnable = Runnable {
      RandomAccessFile(logFile, "r").use { raf ->
        // ブロッキング実行される場合は先頭、そうでない場合は末尾30行目から出力を開始する
        if (blocking) {
          raf.seek(0)
        } else {
          seekToLastNLines(raf, 30)
        }

        // 新しい追加分を追跡する
        while (keepTailing.get()) {
          printRemaining(raf)

          try {
            Thread.sleep(100)
          } catch (e: InterruptedException) {
            break
          }
        }
      }
    }

    keepTailing.set(true)
    if (blocking) {
      // メインスレッドでブロッキング実行
      tailLogRunnable.run()
    } else {
      // 別スレッドで非ブロッキング実行
      tailThread = Thread(tailLogRunnable)
      tailThread!!.isDaemon = true
      tailThread!!.start()
    }
  }

  @JvmStatic
  fun stopTailLog() {
    keepTailing.set(false)
    tailThread?.interrupt()
    tailThread = null
  }

  fun printLines(linesToRead: Int) {
    RandomAccessFile(logFile, "r").use { raf ->
      seekToLastNLines(raf, linesToRead)
      printRemaining(raf)
    }
  }

  private fun seekToLastNLines(raf: RandomAccessFile, linesToRead: Int) {
    var pos = raf.length() // ファイルの末尾 + 1
    var lineFeedCount = 0

    while (pos > 0) {
      pos--
      raf.seek(pos)
      if (raf.read() != '\n'.code) continue

      lineFeedCount++
      if (lineFeedCount > linesToRead) {
        raf.seek(pos + 1)
        return
      }
    }

    raf.seek(0) // ログがN行に満たなければ先頭を指す
  }

  /** raf.seekされた箇所から末尾までを出力する */
  private fun printRemaining(raf: RandomAccessFile) {
    val buffer = ByteArray(8192)
    val initialLength = logFile.length()
    while (raf.filePointer < initialLength) {
      val bytesRead =
        raf.read(buffer, 0, minOf(buffer.size, (initialLength - raf.filePointer).toInt()))

      if (bytesRead > 0) {
        print(String(buffer, 0, bytesRead, StandardCharsets.UTF_8))
      }
    }
  }
}
