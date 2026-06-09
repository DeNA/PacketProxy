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
package packetproxy.cli.api

import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import packetproxy.common.ConfigIO
import packetproxy.model.ListenPort
import packetproxy.model.ListenPorts
import packetproxy.model.Modification
import packetproxy.model.Modifications
import packetproxy.model.Server
import packetproxy.model.Servers

internal class ConfigApiHandler(private val server: ManagementApiServer) {

  private val gson: Gson = server.gson

  // --- /api/config ---

  /**
   * GET /api/config
   *
   * 現在の設定（listenPorts / servers / modifications / sslPassThroughs）を ConfigIO の形式そのままで返す。packetproxy
   * server の --config オプションと同じ JSON フォーマット。
   */
  fun getConfig(): Response {
    val json = ConfigIO().getOptions()
    return server.json200(gson.fromJson(json, Any::class.java))
  }

  /**
   * PUT /api/config
   *
   * ConfigIO 形式の JSON で設定全体を置き換える。既存の設定はすべて削除される。 リクエストボディは GET /api/config の返却値と同じフォーマット。
   */
  fun putConfig(session: IHTTPSession): Response {
    val body = server.bodyJson(session)
    if (body.size() == 0) return server.error400("request body is empty")
    ConfigIO().setOptions(gson.toJson(body))
    return server.jsonOk()
  }

  // --- /api/listenports ---

  fun listenPorts(method: Method, id: Int?, session: IHTTPSession): Response =
    when {
      method == Method.GET && id == null -> {
        val list = ListenPorts.getInstance().queryAll()
        server.json200(mapOf("data" to list))
      }
      method == Method.POST && id == null -> {
        val body = server.bodyJson(session)
        val lp = gson.fromJson(body, ListenPort::class.java)
        ListenPorts.getInstance().create(lp)
        server.jsonOk()
      }
      method == Method.PUT && id != null -> {
        val body = server.bodyJson(session)
        val lp = gson.fromJson(body, ListenPort::class.java)
        lp.setId(id)
        ListenPorts.getInstance().update(lp)
        server.jsonOk()
      }
      method == Method.DELETE && id != null -> {
        ListenPorts.getInstance().delete(id)
        server.jsonOk()
      }
      else -> server.error404()
    }

  // --- /api/servers ---

  fun servers(method: Method, id: Int?, session: IHTTPSession): Response =
    when {
      method == Method.GET && id == null -> {
        val list = Servers.getInstance().queryAll()
        server.json200(mapOf("data" to list))
      }
      method == Method.POST && id == null -> {
        val body = server.bodyJson(session)
        val sv = gson.fromJson(body, Server::class.java)
        Servers.getInstance().create(sv)
        server.jsonOk()
      }
      method == Method.PUT && id != null -> {
        val body = server.bodyJson(session)
        val sv = gson.fromJson(body, Server::class.java)
        sv.setId(id)
        Servers.getInstance().update(sv)
        server.jsonOk()
      }
      method == Method.DELETE && id != null -> {
        val sv = Servers.getInstance().query(id) ?: return server.error404()
        Servers.getInstance().delete(sv)
        server.jsonOk()
      }
      else -> server.error404()
    }

  // --- /api/modifications ---

  fun modifications(method: Method, id: Int?, session: IHTTPSession): Response =
    when {
      method == Method.GET && id == null -> {
        val list = Modifications.getInstance().queryAll()
        server.json200(mapOf("data" to list))
      }
      method == Method.POST && id == null -> {
        val body = server.bodyJson(session)
        val mod = gson.fromJson(body, Modification::class.java)
        Modifications.getInstance().create(mod)
        server.jsonOk()
      }
      method == Method.PUT && id != null -> {
        val body = server.bodyJson(session)
        val mod = gson.fromJson(body, Modification::class.java)
        mod.setId(id)
        Modifications.getInstance().update(mod)
        server.jsonOk()
      }
      method == Method.DELETE && id != null -> {
        Modifications.getInstance().delete(id)
        server.jsonOk()
      }
      else -> server.error404()
    }
}
