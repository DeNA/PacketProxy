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
package packetproxy.common

import java.util.*
import java.util.regex.Pattern
import packetproxy.util.Logging

object I18nString {
  @JvmField val locale: Locale = Locale.getDefault()
  val bundle: ResourceBundle? =
    when (locale) {
      Locale.JAPAN -> ResourceBundle.getBundle("strings")
      else -> null
    }

  @JvmStatic
  fun get(message: String, vararg args: Any?): String {
    return try {
      get(message).format(*args)
    } catch (e: Exception) {
      try {
        message.format(*args)
      } catch (e: Exception) {
        message
      }
    }
  }

  /** propertiesから文字列のローカライズを試みる 失敗した場合や空文字列だった場合は元の文字列を返す */
  @JvmStatic
  fun get(message: String): String {
    val normalized = normalize(message)
    val localized =
      try {
        when (locale) {
          Locale.JAPAN -> bundle!!.getString(normalized)
          else -> null
        }
      } catch (e: MissingResourceException) {
        null
      } catch (e: Exception) {
        Logging.err("[Error] can't read resource: %s", message)
        null
      }

    return localized ?: message
  }

  // "Start listening port %d."のような文字列を"Start_listening_port_%d."に変換する
  private fun normalize(message: String): String {
    return message
      .replace(' ', '_')
      .replace('=', '_')
      .replace(":".toRegex(), "\\:")
      .replace(Pattern.quote("(").toRegex(), "\\(")
      .replace(Pattern.quote(")").toRegex(), "\\)")
  }
}
