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
package packetproxy.extensions.endpointoverview

import java.net.URI
import net.arnx.jsonic.JSON
import packetproxy.http.Http

data class EndpointVariantRow(val summary: EndpointSummary, val fields: Map<String, String>)

object EndpointVariantExtractor {
  private const val STATUS_COLUMN = "status"
  private const val URL_COLUMN = "url"
  private const val MAX_VALUE_LENGTH = 80

  fun buildRows(variants: List<EndpointSummary>): List<EndpointVariantRow> {
    return variants.map { summary ->
      EndpointVariantRow(summary = summary, fields = extractFields(summary))
    }
  }

  fun differingColumns(rows: List<EndpointVariantRow>): List<String> {
    if (rows.isEmpty()) {
      return emptyList()
    }
    if (rows.size == 1) {
      return columnsForSingleRow(rows[0])
    }

    val allKeys = rows.flatMap { it.fields.keys }.toSet().sorted()
    val differing = allKeys.filter { key -> rows.map { it.fields[key] ?: "" }.toSet().size > 1 }
    if (differing.isNotEmpty()) {
      return differing
    }
    return listOf(URL_COLUMN)
  }

  private fun columnsForSingleRow(row: EndpointVariantRow): List<String> {
    val keys = row.fields.keys.filter { it != URL_COLUMN }.sorted()
    if (keys.isNotEmpty()) {
      return keys
    }
    return listOf(URL_COLUMN)
  }

  private fun extractFields(summary: EndpointSummary): Map<String, String> {
    val fields = linkedMapOf<String, String>()
    fields[URL_COLUMN] = summary.url
    fields[STATUS_COLUMN] = summary.formattedStatusCodes()
    fields.putAll(extractQueryFields(summary.url))
    fields.putAll(extractJsonBodyFields(summary))
    return fields
  }

  private fun extractQueryFields(url: String): Map<String, String> {
    val query =
      try {
        URI(url).rawQuery
      } catch (_: Exception) {
        null
      } ?: return emptyMap()

    if (query.isEmpty()) {
      return emptyMap()
    }

    val fields = linkedMapOf<String, String>()
    for (part in query.split("&")) {
      if (part.isEmpty()) {
        continue
      }
      val separator = part.indexOf('=')
      if (separator < 0) {
        fields[part] = ""
      } else {
        val name = part.substring(0, separator)
        val value = part.substring(separator + 1)
        fields[name] = truncate(value)
      }
    }
    return fields
  }

  private fun extractJsonBodyFields(summary: EndpointSummary): Map<String, String> {
    val packet = summary.latestRequestPacket ?: return emptyMap()
    val decodedData = packet.decodedData
    if (decodedData.isEmpty()) {
      return emptyMap()
    }

    return try {
      val http = Http.create(decodedData)
      if (!http.isRequest) {
        return emptyMap()
      }
      val body = http.body ?: return emptyMap()
      if (body.isEmpty()) {
        return emptyMap()
      }
      val trimmed = String(body).trim()
      if (!trimmed.startsWith("{")) {
        return emptyMap()
      }
      parseTopLevelJsonFields(trimmed)
    } catch (_: Exception) {
      emptyMap()
    }
  }

  private fun parseTopLevelJsonFields(json: String): Map<String, String> {
    val decoded = JSON.decode<Any>(json)
    if (decoded !is Map<*, *>) {
      return emptyMap()
    }

    val fields = linkedMapOf<String, String>()
    for ((key, value) in decoded) {
      if (key !is String) {
        continue
      }
      val formatted = formatJsonValue(value) ?: continue
      fields[key] = truncate(formatted)
    }
    return fields
  }

  private fun formatJsonValue(value: Any?): String? {
    return when (value) {
      null -> "null"
      is Boolean -> value.toString()
      is Number -> value.toString()
      is String -> value
      else -> null
    }
  }

  private fun truncate(value: String): String {
    if (value.length <= MAX_VALUE_LENGTH) {
      return value
    }
    return value.substring(0, MAX_VALUE_LENGTH) + "..."
  }
}
