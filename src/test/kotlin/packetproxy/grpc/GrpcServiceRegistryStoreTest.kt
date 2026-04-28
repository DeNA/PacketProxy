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
package packetproxy.grpc

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import packetproxy.model.ListenPort
import packetproxy.model.ListenPorts
import packetproxy.model.Server

class GrpcServiceRegistryStoreTest {
  private fun resource(classpathPath: String): File {
    val u =
      GrpcServiceRegistryStoreTest::class.java.getResource(classpathPath)
        ?: throw IllegalStateException("missing resource: $classpathPath")
    return File(u.toURI())
  }

  private val store: GrpcServiceRegistryStore
    get() = GrpcServiceRegistryStore.getInstance()

  @AfterEach
  fun clearDescriptorCache() {
    store.invalidateAll()
  }

  @Test
  fun get_missingFile_throws() {
    assertThrows(Exception::class.java) { store.get(File("/nonexistent/path.desc")) }
  }

  @Test
  fun get_invalidBytes_throws() {
    val tmp = Files.createTempFile("bad", ".desc").toFile()
    Files.writeString(tmp.toPath(), "not-a-protobuf-descriptor", StandardCharsets.UTF_8)
    assertThrows(Exception::class.java) { store.get(tmp) }
  }

  @Test
  fun get_withIncludeImports_ok_and_cacheReturnsSameInstance() {
    val f = resource("proto/multidir/multi.desc")
    val reg1 = store.get(f)
    assertFalse(reg1.getServiceMethodEntries().isEmpty())
    val reg2 = store.get(f)
    assertSame(reg1, reg2)
  }

  // multi_without_imports.desc was built without --include_imports, so transitive deps are missing.
  // loadAndBuild must detect this and throw rather than silently producing an incomplete registry.
  @Test
  fun get_withoutIncludeImports_throws() {
    val f = resource("proto/multidir/multi_without_imports.desc")
    assertThrows(IllegalStateException::class.java) { store.get(f) }
  }

  @Test
  fun get_testsvc_containsGreeterService() {
    val f = resource("proto/testsvc.desc")
    val reg = store.get(f)
    val entries = reg.getServiceMethodEntries()
    assertTrue(entries.any { it.first == "pp.testsvc.Greeter" && it.second == "SayHello" })
  }

  @Test
  fun get_withIncludeImports_resolvesImportedTopLevelType() {
    val f = resource("proto/multidir/multi.desc")
    val reg = store.get(f)
    val shared = reg.findMessageByName("pp.multidir.Shared")
    val timestamp = reg.findMessageByName("pp.multidir.Timestamp")
    assertNotNull(shared)
    assertNotNull(timestamp)
    assertEquals("Shared", shared!!.name)
    assertEquals("Timestamp", timestamp!!.name)
  }

  @Test
  fun get_withIncludeImports_resolvesImportedNestedType() {
    val f = resource("proto/multidir/multi.desc")
    val reg = store.get(f)
    val detail = reg.findMessageByName("pp.multidir.Shared.Detail")
    assertNotNull(detail)
    assertEquals("Detail", detail!!.name)
  }

  @Test
  fun parseAuthorityHostPort_hostOnly_defaultsTo443() {
    val s = store
    assertEquals("example.com" to 443, s.parseAuthorityHostPort("example.com"))
  }

  @Test
  fun parseAuthorityHostPort_hostAndPort() {
    val s = store
    assertEquals("api.example.com" to 8443, s.parseAuthorityHostPort("api.example.com:8443"))
  }

  @Test
  fun parseAuthorityHostPort_ipv6WithPort() {
    val s = store
    assertEquals("2001:db8::1" to 443, s.parseAuthorityHostPort("[2001:db8::1]:443"))
  }

  @Test
  fun parseAuthorityHostPort_ipv6WithoutPort_defaults443() {
    val s = store
    assertEquals("::1" to 443, s.parseAuthorityHostPort("[::1]"))
  }

  @Test
  fun get_withIncludeImports_resolvesMethodTypesUsingImportedMessages() {
    val f = resource("proto/multidir/multi.desc")
    val reg = store.get(f)
    val input = reg.getInputType("/pp.multidir.ServiceA/Call")
    val output = reg.getOutputType("/pp.multidir.ServiceA/Call")
    assertNotNull(input)
    assertNotNull(output)
    assertEquals("Shared", input!!.name)
    assertEquals("Shared", output!!.name)
  }

  /**
   * Logic shared by [GrpcServiceRegistryStore.getByAuthority] for the transparent-listener case.
   */
  @Test
  fun tryResolveServerViaListenPort_returnsServerFromEnabledListenPort() {
    val listenPorts = mock(ListenPorts::class.java)
    val server = mock(Server::class.java)
    val listenPort = mock(ListenPort::class.java)
    `when`(listenPorts.queryEnabledByPort(ListenPort.Protocol.TCP, 59999)).thenReturn(listenPort)
    `when`(listenPort.getServer()).thenReturn(server)
    val out = store.tryResolveServerViaListenPort(59999, listenPorts)
    assertSame(server, out)
  }

  @Test
  fun tryResolveServerViaListenPort_returnsNullWhenNoMatchingListenPort() {
    val listenPorts = mock(ListenPorts::class.java)
    `when`(listenPorts.queryEnabledByPort(ListenPort.Protocol.TCP, 59999)).thenReturn(null)
    assertNull(store.tryResolveServerViaListenPort(59999, listenPorts))
  }
}
