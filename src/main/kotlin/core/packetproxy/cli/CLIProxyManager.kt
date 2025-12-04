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
package packetproxy.cli

import packetproxy.model.ListenPort
import packetproxy.model.ListenPorts
import packetproxy.model.Server
import packetproxy.model.Servers
import packetproxy.util.Logging

object CLIProxyManager {
    // 遅延初期化: 最初にアクセスされた時に初期化
    private val listenPorts: ListenPorts by lazy {
        println("inited")
        ListenPorts.getInstance()
    }

    private val servers: Servers by lazy {
        Servers.getInstance()
    }

    /**
     * プロキシを開始
     * @param listenPort リッスンするポート番号
     * @param targetHost 転送先ホスト（デフォルト: localhost）
     * @param targetPort 転送先ポート（デフォルト: 8080）
     */
    fun startProxy(listenPort: Int, targetHost: String = "localhost", targetPort: Int = 8080): Result<String> {
        return try {
            // 既に有効なListenPortが存在するかチェック
            val existing = listenPorts.queryEnabledByPort(ListenPort.Protocol.TCP, listenPort)
            if (existing != null) {
                return Result.failure(Exception("ポート $listenPort は既に使用中です"))
            }

            // サーバーを取得または作成
            val server = getOrCreateServer(targetHost, targetPort)
            Logging.log("サーバーID: ${server.getId()}, ホスト: ${server.ip}, ポート: ${server.port}")

            // CAを取得（デフォルトのCAを使用）
            val caName = "PacketProxy per-user CA"

            // ListenPortを作成
            val listenPortInfo = ListenPort(
                listenPort,
                ListenPort.TYPE.FORWARDER,
                server,
                caName
            )
            Logging.log("ListenPort作成: port=${listenPortInfo.port}, type=${listenPortInfo.type}, server_id=${listenPortInfo.serverId}, enabled=${listenPortInfo.isEnabled()}")

            // 有効化してデータベースに保存
            listenPortInfo.setEnabled()
            Logging.log("ListenPort有効化後: server_id=${listenPortInfo.serverId}")
            listenPorts.create(listenPortInfo)

            // ListenPortManagerが自動的にプロキシを開始する
            Logging.log("プロキシを開始しました: ポート $listenPort -> $targetHost:$targetPort")
            Result.success("プロキシを開始しました: ポート $listenPort -> $targetHost:$targetPort")
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            Result.failure(e)
        }
    }

    /**
     * サーバーを取得または作成
     */
    private fun getOrCreateServer(host: String, port: Int): Server {
        return try {
            // 既存のサーバーを検索
            val existing = servers.queryByHostNameAndPort(host, port)
            if (existing != null) {
                Logging.log("既存のサーバーを取得: id=${existing.getId()}, host=$host, port=$port")
                return existing
            }

            // 存在しない場合は新規作成
            val server = Server(
                host,
                port,
                false,  // use_ssl
                "",     // encoder
                false,  // resolved_by_dns
                false,  // resolved_by_dns6
                false,  // http_proxy
                "CLI Mode"  // comment
            )
            Logging.log("サーバーを作成前: id=${server.getId()}, host=$host, port=$port")
            servers.create(server)
            Logging.log("サーバーを作成後: id=${server.getId()}, host=$host, port=$port")

            // createIfNotExists()の後、IDが正しく設定されるように再取得
            // 既に存在する場合も含めて、正しいIDを持つServerオブジェクトを取得
            val createdServer = servers.queryByHostNameAndPort(host, port)
                ?: throw Exception("サーバーの作成に失敗しました")
            Logging.log("サーバーを再取得後: id=${createdServer.getId()}, host=$host, port=$port")
            return createdServer
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            throw e
        }
    }

    /**
     * プロキシを停止
     */
    fun stopProxy(listenPort: Int): Result<String> {
        return try {
            val listenPortInfo = listenPorts.queryEnabledByPort(ListenPort.Protocol.TCP, listenPort)
                ?: return Result.failure(Exception("ポート $listenPort は起動していません"))

            // 無効化して更新
            listenPortInfo.setDisabled()
            listenPorts.update(listenPortInfo)

            // ListenPortManagerが自動的にプロキシを停止する
            Logging.log("プロキシを停止しました: ポート $listenPort")
            Result.success("プロキシを停止しました: ポート $listenPort")
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            Result.failure(e)
        }
    }

    /**
     * すべてのプロキシを停止
     */
    fun stopAll(): Unit {
        try {
            listenPorts.queryEnabled().forEach { port ->
                stopProxy(port.port)
            }
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
        }
    }

    /**
     * 起動中のプロキシ一覧を取得
     */
    fun listProxies(): Map<Int, String> {
        return try {
            val enabledPorts = listenPorts.queryEnabled()
            enabledPorts
                .filter { it.type == ListenPort.TYPE.FORWARDER }
                .associate { port ->
                    try {
                        val server = port.getServer()
                        port.port to "${server.ip}:${server.port}"
                    } catch (e: Exception) {
                        port.port to "unknown"
                    }
                }
        } catch (e: Exception) {
            Logging.errWithStackTrace(e)
            emptyMap()
        }
    }

    /**
     * 起動中のプロキシがあるか
     */
    fun hasActiveProxies(): Boolean {
        return try {
            listenPorts.queryEnabled().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
