/*
 * Copyright 2019 DeNA Co., Ltd.
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
package packetproxy.extensions.securityheaders

import packetproxy.http.HttpHeader

/**
 * Helper class to create HttpHeader instances for testing. Builds HTTP response headers with
 * specified header fields.
 */
class TestHttpHeader {
  private val headerBuilder: StringBuilder

  constructor() {
    headerBuilder = StringBuilder()
    headerBuilder.append("HTTP/1.1 200 OK\r\n")
  }

  constructor(statusLine: String) {
    headerBuilder = StringBuilder()
    headerBuilder.append(statusLine).append("\r\n")
  }

  fun addHeader(name: String, value: String): TestHttpHeader {
    headerBuilder.append(name).append(": ").append(value).append("\r\n")
    return this
  }

  fun build(): HttpHeader {
    headerBuilder.append("\r\n")
    return HttpHeader(headerBuilder.toString().toByteArray())
  }

  companion object {
    // ===== Static Factory Methods for Common Test Cases =====

    @JvmStatic
    fun empty(): HttpHeader {
      return TestHttpHeader().build()
    }

    @JvmStatic
    fun withCsp(cspValue: String): HttpHeader {
      return TestHttpHeader().addHeader("Content-Security-Policy", cspValue).build()
    }

    @JvmStatic
    fun withXFrameOptions(xfoValue: String): HttpHeader {
      return TestHttpHeader().addHeader("X-Frame-Options", xfoValue).build()
    }

    @JvmStatic
    fun withSetCookie(cookieValue: String): HttpHeader {
      return TestHttpHeader().addHeader("Set-Cookie", cookieValue).build()
    }

    @JvmStatic
    fun withHsts(hstsValue: String): HttpHeader {
      return TestHttpHeader().addHeader("Strict-Transport-Security", hstsValue).build()
    }

    @JvmStatic
    fun withContentType(contentTypeValue: String): HttpHeader {
      return TestHttpHeader().addHeader("Content-Type", contentTypeValue).build()
    }

    @JvmStatic
    fun withCacheControl(cacheControlValue: String): HttpHeader {
      return TestHttpHeader().addHeader("Cache-Control", cacheControlValue).build()
    }

    @JvmStatic
    fun withCors(corsValue: String): HttpHeader {
      return TestHttpHeader().addHeader("Access-Control-Allow-Origin", corsValue).build()
    }

    @JvmStatic
    fun withXContentTypeOptions(value: String): HttpHeader {
      return TestHttpHeader().addHeader("X-Content-Type-Options", value).build()
    }
  }
}
