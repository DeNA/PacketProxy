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

import javax.swing.JFrame
import javax.swing.JOptionPane

object MergedRowDataResolver {
  /**
   * マージ行（Request+Response両方ある行）の場合はどちらのデータを使うかユーザに選択させる。 単一パケット行の場合は request
   * データをそのまま返す。ダイアログでキャンセルされた場合は null を返す。
   */
  fun resolve(
    owner: JFrame,
    message: String,
    title: String,
    isMergedRow: Boolean,
    requestData: () -> ByteArray?,
    responseData: () -> ByteArray?,
  ): ByteArray? {
    if (!isMergedRow) {
      return requestData()
    }
    // macOS の JOptionPane はボタンを右から左に描画するため、
    // 視覚的に左から「Request | Response」の順にするには逆順で定義する。
    val options = arrayOf("Response", "Request")
    val choice =
      JOptionPane.showOptionDialog(
        owner,
        message,
        title,
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        null,
      )
    if (choice == JOptionPane.CLOSED_OPTION) {
      return null
    }
    if (choice == 0) {
      return responseData()
    }
    return requestData()
  }

  fun isSelectedRowMerged(): Boolean = GUIHistory.getInstance().isSelectedRowMerged()
}
