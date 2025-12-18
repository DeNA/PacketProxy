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
import java.nio.file.Files
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

  /** CLIモードでのlogの継続出力を行う */
  fun startTailLog() {
    if (tailThread != null || !isGulp) return

    tailThread = Thread {
      val raf = RandomAccessFile(logFile, "r")
      try {
        // ファイルの末尾から開始
        raf.seek(raf.length())

        while (keepTailing.get()) {
          val currentLength = logFile.length()
          val buffer = ByteArray(8192)
          var bytesRead: Int

          while (raf.filePointer < currentLength) {
            bytesRead =
              raf.read(buffer, 0, minOf(buffer.size, (currentLength - raf.filePointer).toInt()))

            if (bytesRead > 0) {
              print(String(buffer, 0, bytesRead, StandardCharsets.UTF_8))
            }
          }

          try {
            Thread.sleep(100)
          } catch (e: InterruptedException) {}
        }
      } finally {
        raf.close()
      }
    }

    keepTailing.set(true)
    tailThread!!.isDaemon = true
    tailThread!!.start()
  }

  fun stopTailLog() {
    keepTailing.set(false)
    tailThread?.interrupt()
    tailThread = null
  }

  fun printLog(lineCount: Int) {
    val lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8)
    val startIndex = maxOf(0, lines.size - lineCount)
    lines.subList(startIndex, lines.size).forEach { println(it) }
  }
}
