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
package packetproxy.extensions.securityheaders

/** Represents the result of a security header check. */
class SecurityCheckResult(val status: Status, displayValue: String?, rawValue: String?) {
  enum class Status {
    OK,
    FAIL,
    WARN,
  }

  val displayValue: String = displayValue ?: status.name
  val rawValue: String = rawValue ?: ""

  val isOk: Boolean
    get() = status == Status.OK

  val isFail: Boolean
    get() = status == Status.FAIL

  val isWarn: Boolean
    get() = status == Status.WARN

  companion object {
    @JvmStatic
    fun ok(displayValue: String?, rawValue: String?): SecurityCheckResult {
      return SecurityCheckResult(Status.OK, displayValue, rawValue)
    }

    @JvmStatic
    fun fail(displayValue: String?, rawValue: String?): SecurityCheckResult {
      return SecurityCheckResult(Status.FAIL, displayValue, rawValue)
    }

    @JvmStatic
    fun warn(displayValue: String?, rawValue: String?): SecurityCheckResult {
      return SecurityCheckResult(Status.WARN, displayValue, rawValue)
    }
  }
}
