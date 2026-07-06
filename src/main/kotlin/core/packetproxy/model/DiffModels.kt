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
package packetproxy.model

object DiffModels {
  fun markAsOriginal(data: ByteArray) {
    Diff.getInstance().markAsOriginal(data)
    DiffBinary.getInstance().markAsOriginal(data)
    DiffJson.getInstance().markAsOriginal(data)
  }

  fun markAsTarget(data: ByteArray) {
    Diff.getInstance().markAsTarget(data)
    DiffBinary.getInstance().markAsTarget(data)
    DiffJson.getInstance().markAsTarget(data)
  }

  fun clearOriginal() {
    Diff.getInstance().clearAsOriginal()
    DiffBinary.getInstance().clearAsOriginal()
    DiffJson.getInstance().clearAsOriginal()
  }
}
