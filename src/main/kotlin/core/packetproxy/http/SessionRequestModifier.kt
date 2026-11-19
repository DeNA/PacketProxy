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
package packetproxy.http

import packetproxy.model.SessionProfile

object SessionRequestModifier {
  @JvmStatic
  fun apply(decodedBytes: ByteArray, profile: SessionProfile?): ByteArray {
    if (profile == null) {
      return decodedBytes
    }

    if (!HttpHeader.isHTTPHeader(decodedBytes)) {
      return decodedBytes
    }

    try {
      val http = Http.create(decodedBytes)
      if (!http.isRequest) {
        return decodedBytes
      }

      applyAuthorization(http, profile.authorization)
      return http.toByteArray()
    } catch (_: Exception) {
      return decodedBytes
    }
  }

  private fun applyAuthorization(http: Http, authorization: String?) {
    if (authorization == null) {
      return
    }
    if (authorization.isEmpty()) {
      http.removeHeader("Authorization")
      return
    }
    http.updateHeader("Authorization", authorization)
  }
}
