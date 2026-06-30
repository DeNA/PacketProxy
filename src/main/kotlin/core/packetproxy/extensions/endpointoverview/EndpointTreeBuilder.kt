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
import javax.swing.tree.DefaultMutableTreeNode

data class PathSegment(val fullPrefix: String, val segmentLabel: String)

object EndpointTreeBuilder {
  fun buildPathSegments(path: String): List<PathSegment> {
    val segments = path.split("/").filter { it.isNotEmpty() }
    var cumulative = ""
    return segments.map { segment ->
      cumulative = "$cumulative/$segment"
      PathSegment(fullPrefix = cumulative, segmentLabel = "/$segment")
    }
  }

  fun build(endpoints: Collection<EndpointSummary>): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode()
    val hostNodes = mutableMapOf<String, DefaultMutableTreeNode>()
    val pathNodes = mutableMapOf<Pair<String, String>, DefaultMutableTreeNode>()
    val methodBuckets = mutableMapOf<Triple<String, String, String>, MutableList<EndpointSummary>>()
    val methodBucketParents = mutableMapOf<Triple<String, String, String>, DefaultMutableTreeNode>()

    val sorted =
      endpoints.sortedWith(compareBy<EndpointSummary>({ it.host }, { it.url }, { it.method }))

    for (summary in sorted) {
      val hostNode =
        hostNodes.getOrPut(summary.host) {
          DefaultMutableTreeNode(EndpointTreeHost(summary.host)).also { root.add(it) }
        }

      val path =
        try {
          URI(summary.url).path ?: ""
        } catch (_: Exception) {
          ""
        }

      var current = hostNode
      var fullPathPrefix = ""
      for (segment in buildPathSegments(path)) {
        val key = summary.host to segment.fullPrefix
        val pathNode =
          pathNodes.getOrPut(key) {
            DefaultMutableTreeNode(EndpointTreeFolder(segment.fullPrefix, segment.segmentLabel))
              .also { current.add(it) }
          }
        current = pathNode
        fullPathPrefix = segment.fullPrefix
      }

      val methodKey = Triple(summary.host, fullPathPrefix, summary.method)
      methodBuckets.getOrPut(methodKey) { mutableListOf() }.add(summary)
      methodBucketParents[methodKey] = current
    }

    for ((methodKey, summaries) in methodBuckets) {
      val parent = methodBucketParents[methodKey] ?: continue
      val method = methodKey.third
      parent.add(DefaultMutableTreeNode(EndpointTreeMethod(method, summaries.toList())))
    }

    return root
  }
}
