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

import javax.swing.tree.DefaultMutableTreeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndpointTreeBuilderTest {
  @Test
  fun buildPathSegments_splitsPathIntoSegmentLabels() {
    val segments = buildPathSegments("/api/users/foo")

    assertEquals(listOf("/api", "/users", "/foo"), segments.map { it.segmentLabel })
    assertEquals(listOf("/api", "/api/users", "/api/users/foo"), segments.map { it.fullPrefix })
  }

  @Test
  fun buildPathSegments_emptyPath_returnsEmptyList() {
    assertTrue(buildPathSegments("").isEmpty())
    assertTrue(buildPathSegments("/").isEmpty())
  }

  @Test
  fun build_singleEndpoint_createsMethodNodeWithoutLeaf() {
    val summary = createSummary("GET", "https://example.com/api/users", "example.com")

    val root = EndpointTreeBuilder.build(listOf(summary))
    val hostNode = root.getChildAt(0) as DefaultMutableTreeNode
    val apiNode = hostNode.getChildAt(0) as DefaultMutableTreeNode
    val usersNode = apiNode.getChildAt(0) as DefaultMutableTreeNode
    val methodNode = usersNode.getChildAt(0) as DefaultMutableTreeNode

    assertInstanceOf(EndpointTreeHost::class.java, hostNode.userObject)
    assertEquals("example.com", (hostNode.userObject as EndpointTreeHost).host)

    val apiFolder = apiNode.userObject as EndpointTreeFolder
    assertEquals("/api", apiFolder.segmentLabel)
    assertEquals("/api", apiFolder.fullPathPrefix)

    val usersFolder = usersNode.userObject as EndpointTreeFolder
    assertEquals("/users", usersFolder.segmentLabel)
    assertEquals("/api/users", usersFolder.fullPathPrefix)

    val method = methodNode.userObject as EndpointTreeMethod
    assertEquals("GET", method.method)
    assertEquals(summary, method.summary)
    assertEquals(0, methodNode.childCount)
  }

  @Test
  fun build_apiAndApiUsersEndpoints_shareApiFolderWithSiblingMethodAndChildFolder() {
    val apiSummary = createSummary("GET", "https://example.com/api", "example.com")
    val usersSummary = createSummary("GET", "https://example.com/api/users", "example.com")

    val root = EndpointTreeBuilder.build(listOf(apiSummary, usersSummary))
    val hostNode = root.getChildAt(0) as DefaultMutableTreeNode
    val apiNode = hostNode.getChildAt(0) as DefaultMutableTreeNode

    assertEquals("/api", (apiNode.userObject as EndpointTreeFolder).segmentLabel)
    assertEquals(2, apiNode.childCount)

    val firstChild = (apiNode.getChildAt(0) as DefaultMutableTreeNode).userObject
    val secondChild = (apiNode.getChildAt(1) as DefaultMutableTreeNode).userObject

    val hasMethodNode = firstChild is EndpointTreeMethod || secondChild is EndpointTreeMethod
    val hasUsersFolder =
      (firstChild as? EndpointTreeFolder)?.fullPathPrefix == "/api/users" ||
        (secondChild as? EndpointTreeFolder)?.fullPathPrefix == "/api/users"

    assertTrue(hasMethodNode)
    assertTrue(hasUsersFolder)
  }

  @Test
  fun build_samePathDifferentMethods_createsMethodNodesAsSiblings() {
    val getSummary = createSummary("GET", "https://example.com/api/users", "example.com")
    val postSummary = createSummary("POST", "https://example.com/api/users", "example.com")

    val root = EndpointTreeBuilder.build(listOf(getSummary, postSummary))
    val hostNode = root.getChildAt(0) as DefaultMutableTreeNode
    val apiNode = hostNode.getChildAt(0) as DefaultMutableTreeNode
    val usersNode = apiNode.getChildAt(0) as DefaultMutableTreeNode

    assertEquals("/users", (usersNode.userObject as EndpointTreeFolder).segmentLabel)
    assertEquals(2, usersNode.childCount)
    val methods =
      (0 until usersNode.childCount)
        .map {
          ((usersNode.getChildAt(it) as DefaultMutableTreeNode).userObject as EndpointTreeMethod)
            .method
        }
        .toSet()
    assertEquals(setOf("GET", "POST"), methods)
  }

  @Test
  fun build_samePathSameMethodDifferentQueries_createsQueryLeavesUnderMethodNode() {
    val firstSummary =
      createSummary(
        "GET",
        "https://example.com/api/users?id=1",
        "example.com",
        statusCodes = setOf("200"),
      )
    val secondSummary =
      createSummary(
        "GET",
        "https://example.com/api/users?id=2",
        "example.com",
        statusCodes = setOf("404"),
      )

    val root = EndpointTreeBuilder.build(listOf(firstSummary, secondSummary))
    val usersNode = findPathFolder(root, "/api/users")
    val methodNode = usersNode.getChildAt(0) as DefaultMutableTreeNode

    val method = methodNode.userObject as EndpointTreeMethod
    assertEquals("GET", method.method)
    assertNull(method.summary)
    assertEquals(2, methodNode.childCount)

    val displayNames =
      (0 until methodNode.childCount)
        .map {
          ((methodNode.getChildAt(it) as DefaultMutableTreeNode).userObject as EndpointTreeLeaf)
            .displayName
        }
        .toSet()

    assertTrue(displayNames.any { it.contains("?id=1") })
    assertTrue(displayNames.any { it.contains("?id=2") })
  }

  @Test
  fun build_singleQueryParam_noLeaf() {
    val summary =
      createSummary(
        "GET",
        "https://example.com/api/users?id=1",
        "example.com",
        statusCodes = setOf("200"),
      )

    val root = EndpointTreeBuilder.build(listOf(summary))
    val usersNode = findPathFolder(root, "/api/users")
    val methodNode = usersNode.getChildAt(0) as DefaultMutableTreeNode
    val method = methodNode.userObject as EndpointTreeMethod

    assertEquals(summary, method.summary)
    assertEquals(0, methodNode.childCount)
    assertTrue(method.displayName.contains("?id=1"))
    assertTrue(method.displayName.contains("GET"))
  }

  @Test
  fun build_queryUrl_includesQueryInMethodDisplayNameWhenSingleEntry() {
    val summary =
      createSummary(
        "GET",
        "https://example.com/api/users?id=1",
        "example.com",
        statusCodes = setOf("200"),
      )

    val root = EndpointTreeBuilder.build(listOf(summary))
    val method = findMethodNode(root)

    assertTrue(method.displayName.contains("?id=1"))
    assertTrue(method.displayName.contains("GET"))
  }

  @Test
  fun build_noQueryUrl_showsStatsOnMethodNodeWithoutLeaf() {
    val summary = createSummary("GET", "https://example.com/api/users", "example.com")

    val root = EndpointTreeBuilder.build(listOf(summary))
    val method = findMethodNode(root)

    assertEquals("GET  [200]", method.displayName)
    assertEquals(summary, method.summary)
  }

  private fun buildPathSegments(path: String): List<PathSegment> =
    EndpointTreeBuilder.buildPathSegments(path)

  private fun createSummary(
    method: String,
    url: String,
    host: String,
    statusCodes: Set<String> = setOf("200"),
  ): EndpointSummary {
    return EndpointSummary(
      method = method,
      url = url,
      host = host,
      statusCodes = statusCodes.toMutableSet(),
      contentTypes = mutableSetOf(),
    )
  }

  private fun findPathFolder(
    root: DefaultMutableTreeNode,
    fullPathPrefix: String,
  ): DefaultMutableTreeNode {
    for (i in 0 until root.childCount) {
      val child = root.getChildAt(i) as DefaultMutableTreeNode
      if (
        child.userObject is EndpointTreeFolder &&
          (child.userObject as EndpointTreeFolder).fullPathPrefix == fullPathPrefix
      ) {
        return child
      }
      try {
        return findPathFolder(child, fullPathPrefix)
      } catch (_: AssertionError) {
        // continue searching siblings
      }
    }
    throw AssertionError("Path folder not found: $fullPathPrefix")
  }

  private fun findMethodNode(node: DefaultMutableTreeNode): EndpointTreeMethod {
    if (node.userObject is EndpointTreeMethod) {
      return node.userObject as EndpointTreeMethod
    }
    for (i in 0 until node.childCount) {
      val child = node.getChildAt(i) as DefaultMutableTreeNode
      try {
        return findMethodNode(child)
      } catch (_: AssertionError) {
        // continue
      }
    }
    throw AssertionError("No method node found")
  }
}
