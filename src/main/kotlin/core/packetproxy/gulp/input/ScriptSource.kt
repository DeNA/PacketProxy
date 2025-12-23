package packetproxy.gulp.input

import java.io.File
import java.io.RandomAccessFile

/** 指定されたファイルの内容を1行ずつ読み取りを実行する */
class ScriptSource(private val filePath: String) : LineSource {
  private val sourceFile: File = File(filePath)
  private val raf: RandomAccessFile?

  // ファイルが存在し、ファイルであり、読み取り可能かチェック
  private val isReadable: Boolean =
    sourceFile.exists() && sourceFile.isFile() && sourceFile.canRead()

  init {
    raf =
      if (isReadable) {
        try {
          RandomAccessFile(sourceFile, "r").apply { seek(0) }
        } catch (e: Exception) {
          // ファイルが読み取り可能でも、RandomAccessFileの作成に失敗した場合はnull
          null
        }
      } else {
        null
      }
  }

  override fun open() {}

  // ファイルが読み取り可能な場合のみ読み取りを実行し、それ以外の場合は常にnullを返す
  override fun readLine(): String? {
    return if (isReadable && raf != null) {
      try {
        raf.readLine()
      } catch (e: Exception) {
        null
      }
    } else {
      null
    }
  }

  override fun close() {
    raf?.close()
  }
}
