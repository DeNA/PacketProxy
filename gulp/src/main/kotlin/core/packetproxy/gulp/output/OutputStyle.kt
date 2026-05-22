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
package packetproxy.gulp.output

/**
 * 出力スタイル（色付け）を提供するインターフェース
 *
 * ConsoleOutputではANSIエスケープシーケンスを含むスタイル、 BufferedOutputでは空文字列を返すスタイルを使用することで、
 * 標準出力では色付き、内部連携では色なしのテキストを生成できる。
 *
 * 使用例:
 * ```
 * ctx.println("${ctx.style.green}Success${ctx.style.reset}")
 * ctx.println(ctx.style.success("Operation completed"))
 * ```
 */
interface OutputStyle {
  val reset: String
  val bold: String

  // 前景色
  val black: String
  val red: String
  val green: String
  val yellow: String
  val blue: String
  val magenta: String
  val cyan: String
  val white: String

  /** 指定した色でテキストを装飾する */
  fun colored(text: String, color: String): String = "$color$text$reset"

  /** 成功メッセージ用（緑色） */
  fun success(text: String): String = colored(text, green)

  /** エラーメッセージ用（赤色） */
  fun error(text: String): String = colored(text, red)

  /** 警告メッセージ用（黄色） */
  fun warning(text: String): String = colored(text, yellow)

  /** 情報メッセージ用（シアン） */
  fun info(text: String): String = colored(text, cyan)
}

/**
 * ANSIエスケープシーケンスを使用した色付きスタイル 標準出力（ConsoleOutput）で使用
 *
 * Note: Logging.ktではorg.jline.jansi.Ansiのビルダーパターンを使用しているが、
 * OutputStyleインターフェース（文字列プロパティベース）とは設計が異なる。 ANSIエスケープコード自体は標準仕様のため、独自に定数を定義する。
 */
object AnsiStyle : OutputStyle {
  override val reset = "\u001B[0m"
  override val bold = "\u001B[1m"

  override val black = "\u001B[30m"
  override val red = "\u001B[31m"
  override val green = "\u001B[32m"
  override val yellow = "\u001B[33m"
  override val blue = "\u001B[34m"
  override val magenta = "\u001B[35m"
  override val cyan = "\u001B[36m"
  override val white = "\u001B[37m"
}

/** 色なしスタイル（全て空文字列） バッファ出力（BufferedOutput）や内部連携で使用 */
object PlainStyle : OutputStyle {
  override val reset = ""
  override val bold = ""

  override val black = ""
  override val red = ""
  override val green = ""
  override val yellow = ""
  override val blue = ""
  override val magenta = ""
  override val cyan = ""
  override val white = ""
}
