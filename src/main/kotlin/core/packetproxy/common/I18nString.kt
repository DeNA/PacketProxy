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
import packetproxy.util.Logging.err

object I18nString {
  var bundle: ResourceBundle? = null
  var locale: Locale? = null

  init {
    locale = Locale.getDefault()
    if (locale === Locale.JAPAN) {
      bundle = ResourceBundle.getBundle("strings")
    }
  }

  private fun normalize(message: String): String {
    return message
      .replace(' ', '_')
      .replace('=', '_')
      .replace(":".toRegex(), "\\:")
      .replace(Pattern.quote("(").toRegex(), "\\(")
      .replace(Pattern.quote(")").toRegex(), "\\)")
  }

  @JvmStatic
  fun get(message: String, vararg args: Any?): String {
    val localed = I18nString.get(message)
    try {
      return String.format(localed, *args)
    } catch (e: Exception) {
      return String.format(message, *args)
    }
  }

  @JvmStatic
  fun get(message: String): String {
    if (locale === Locale.JAPAN) {
      try {
        val localeMsg = bundle!!.getString(normalize(message))
        return if (localeMsg.length > 0) localeMsg else message
      } catch (e: MissingResourceException) {
        return message
      } catch (e: Exception) {
        err("[Error] can't read resource: %s", message)
        return message
      }
    }
    return message
  }
}
