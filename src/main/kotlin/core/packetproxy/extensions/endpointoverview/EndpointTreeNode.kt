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

sealed interface EndpointTreeNode {
  val displayName: String
}

data class EndpointTreeRoot(val name: String = "Endpoints") : EndpointTreeNode {
  override val displayName: String = name
}

data class EndpointTreeHost(val host: String) : EndpointTreeNode {
  override val displayName: String = host
}

data class EndpointTreeFolder(val fullPathPrefix: String, val segmentLabel: String) :
  EndpointTreeNode {
  override val displayName: String = segmentLabel
}

data class EndpointTreeMethod(val method: String, val summary: EndpointSummary? = null) :
  EndpointTreeNode {
  override val displayName: String
    get() {
      if (summary == null) {
        return method
      }
      return formatEndpointLabel(method, summary)
    }
}

data class EndpointTreeLeaf(val summary: EndpointSummary) : EndpointTreeNode {
  override val displayName: String
    get() = formatEndpointLabel(method = null, summary = summary)
}

internal fun formatEndpointLabel(method: String?, summary: EndpointSummary): String {
  val query =
    try {
      URI(summary.url).rawQuery
    } catch (_: Exception) {
      null
    }

  val stats = "[${summary.formattedStatusCodes()}]"
  if (method == null) {
    val queryPart = if (query != null && query.isNotEmpty()) "?$query  " else ""
    return "$queryPart$stats"
  }

  val queryPart = if (query != null && query.isNotEmpty()) " ?$query  " else "  "
  return "$method$queryPart$stats"
}
