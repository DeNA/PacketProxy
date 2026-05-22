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
 * コマンド出力の抽象化インターフェース
 *
 * 標準出力への直接呼び出しを避け、テスト容易性と出力先の柔軟性を実現する。
 *
 * 主な実装:
 * - ConsoleOutput: 標準出力（本番用、色付きサポート）
 * - BufferedOutput: バッファ蓄積（テスト・内部連携用、色なし）
 */
interface CommandOutput {
  /**
   * 色付きテキストを生成するためのスタイルオブジェクト ConsoleOutput: ANSIエスケープシーケンスを含むスタイル BufferedOutput: 空文字列を返すスタイル（色なし）
   */
  val style: OutputStyle

  /** テキストを出力する（改行付き） */
  fun println(text: String = "")

  /** テキストを出力する（改行なし） */
  fun print(text: String)

  /** 出力済みの内容を取得する ConsoleOutputでは空文字列を返す（キャプチャしない） BufferedOutputでは蓄積された内容を返す */
  fun getOutput(): String

  /** 出力バッファをクリアする */
  fun clear()
}
