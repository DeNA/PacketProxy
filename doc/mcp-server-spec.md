# PacketProxy MCP サーバー仕様書

## 概要

PacketProxy MCP サーバーは、Model Context Protocol (MCP) を使用してPacketProxyの機能を外部から操作するためのインターフェースを提供します。AIエージェントやその他のクライアントがPacketProxyのパケット履歴取得、設定変更、パケット再送などの操作を実行できます。

## 基本仕様

- **プロトコル**: MCP (Model Context Protocol) - JSON-RPC over stdin/stdout
- **補完API**: HTTP REST API (localhost:32350)
- **認証**: アクセストークン方式
- **実装**: PacketProxy Extension として実装

## アーキテクチャ

```
┌─────────────────┐    JSON-RPC     ┌─────────────────┐
│   MCP Client    │ ←─────────────→ │  MCP Extension  │
│  (Claude, etc)  │   stdin/stdout  │                 │
└─────────────────┘                 └─────────────────┘
                                             │
                                             ▼
                                    ┌─────────────────┐
                                    │  PacketProxy    │
                                    │   Core APIs     │
                                    └─────────────────┘
```

## 認証

**すべてのMCPツール**は認証が必要です。各ツールの呼び出し時に`access_token`パラメータを指定する必要があります。

### アクセストークンの取得方法

1. PacketProxyの**Settings**タブを開く
2. **Import/Export configs (Experimental)**セクションを見つける
3. **Enabled**チェックボックスを有効にする
4. 自動生成された**AccessToken**をコピーする
5. MCPツール呼び出し時に`access_token`パラメータとして使用する

### 環境変数による自動認証 (推奨)

MCP HTTP Bridgeを使用する場合、環境変数にアクセストークンを設定することで、自動的に認証情報が追加されます：

```bash
export PACKETPROXY_ACCESS_TOKEN="your_access_token_here"
```

この設定により、各ツール呼び出し時に手動で`access_token`パラメータを指定する必要がなくなります。

### 認証エラーの場合

- アクセストークンが未設定: PacketProxyでconfig sharingを有効にしてください
- アクセストークンが無効: Settings画面で正しいトークンを確認してください
- アクセストークンが空: 必須パラメータのため、必ず指定してください
- 環境変数が設定されていない場合: `PACKETPROXY_ACCESS_TOKEN`環境変数を確認してください

## MCPツール一覧

### 1. `get_history` - パケット履歴取得

PacketProxyのパケット履歴を検索・取得します。フィルタリング、並び順指定、ページング機能を提供します。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_history",
    "arguments": {
      "access_token": "your_access_token_here",
      "limit": 100,
      "offset": 0,
      "filter": "method == GET && url =~ /api/",
      "order": "time desc"
    }
  },
  "id": 1
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `limit` (number, optional): 取得件数 (デフォルト: 100)
- `offset` (number, optional): オフセット (デフォルト: 0)
- `filter` (string, optional): PacketProxy Filter構文による絞り込み
- `order` (string, optional): 並び順指定 (デフォルト: "id desc")
- 形式: `"カラム名 方向"` (例: `"time desc"`, `"length asc"`)
- 対応カラム: id, length, client_ip, client_port, server_ip, server_port, time, resend, modified, type, encode, group, method, url, status
- 方向: `asc` (昇順) または `desc` (降順)

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "packets": [
      {
        "id": 123,
        "method": "GET",
        "url": "/api/users",
        "status": 200,
        "length": 1024,
        "time": "2025-01-15T10:30:00Z",
        "server_name": "api.example.com",
        "client_ip": "192.168.1.100"
      }
    ],
    "total_count": 1500,
    "has_more": true,
    "filter_applied": "method == GET && url =~ /api/",
    "order_applied": "time desc"
  },
  "id": 1
}
```

### 2. `get_packet_detail` - パケット詳細取得

特定のパケットの詳細情報を取得します。指定したパケットIDがリクエストの場合は対応するレスポンスも、レスポンスの場合は対応するリクエストも同時に返します。ペア取得機能は`include_pair`オプションで制御できます。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_packet_detail",
    "arguments": {
      "access_token": "your_access_token_here",
      "packet_id": 123,
      "include_body": true,
      "include_pair": false
    }
  },
  "id": 2
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `packet_id` (number, required): パケットID（リクエストまたはレスポンスのどちらでも指定可能）
- `include_body` (boolean, optional): リクエスト/レスポンスボディを含める (デフォルト: true)
- `include_pair` (boolean, optional): ペアパケット（リクエスト指定時はレスポンス、レスポンス指定時はリクエスト）を含める (デフォルト: false)

**レスポンス（ペアが見つかった場合）:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "paired": true,
    "requested_packet_id": 123,
    "group": 1001,
    "conn": 5,
    "request": {
      "id": 123,
      "direction": "client",
      "method": "GET",
      "url": "/api/users",
      "version": "HTTP/1.1",
      "headers": [
        {"name": "Host", "value": "api.example.com"},
        {"name": "User-Agent", "value": "Mozilla/5.0..."}
      ],
      "body": "",
      "length": 256,
      "time": "2025-01-15T10:30:00Z",
      "resend": false,
      "modified": false,
      "type": "HTTP",
      "encode": "HTTP",
      "client": {"ip": "192.168.1.100", "port": 54321},
      "server": {"ip": "192.168.1.1", "port": 80}
    },
    "response": {
      "id": 124,
      "direction": "server",
      "status": 200,
      "status_text": "OK",
      "headers": [
        {"name": "Content-Type", "value": "application/json"},
        {"name": "Content-Length", "value": "1024"}
      ],
      "body": "{\"users\": [...]}",
      "length": 1024,
      "time": "2025-01-15T10:30:01Z",
      "resend": false,
      "modified": false,
      "type": "HTTP",
      "encode": "HTTP",
      "client": {"ip": "192.168.1.100", "port": 54321},
      "server": {"ip": "192.168.1.1", "port": 80}
    }
  },
  "id": 2
}
```

**レスポンス（ペアが見つからない場合）:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "paired": false,
    "requested_packet_id": 123,
    "group": 1001,
    "conn": 5,
    "request": {
      "id": 123,
      "direction": "client",
      "method": "GET",
      "url": "/api/users",
      "version": "HTTP/1.1",
      "headers": [
        {"name": "Host", "value": "api.example.com"},
        {"name": "User-Agent", "value": "Mozilla/5.0..."}
      ],
      "body": "",
      "length": 256,
      "time": "2025-01-15T10:30:00Z",
      "resend": false,
      "modified": false,
      "type": "HTTP",
      "encode": "HTTP",
      "client": {"ip": "192.168.1.100", "port": 54321},
      "server": {"ip": "192.168.1.1", "port": 80}
    },
    "response": null
  },
  "id": 2
}
```

**主な機能:**
- **ペア検索機能**: 指定されたパケットに対応するリクエスト/レスポンスを自動的に検索（`include_pair=true`の場合）
- **統一レスポンス形式**: リクエストIDを指定してもレスポンスIDを指定しても、同じ形式でリクエスト/レスポンス両方の詳細を返す
- **ペア情報**: `paired`フィールドでペアが見つかったかどうかを示す
- **詳細情報の追加**: 各パケットに`direction`（client/server）フィールドを追加
- **接続情報**: `group`と`conn`フィールドでパケット間の関連性を示す
- **ペア取得制御**: `include_pair=false`を指定することで、指定したパケットのみを取得可能

### 3. `get_logs` - ログ取得

PacketProxyのログを取得します。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_logs",
    "arguments": {
      "access_token": "your_access_token_here",
      "level": "info",
      "limit": 100,
      "since": "2025-01-15T00:00:00Z",
      "filter": "error|exception"
    }
  },
  "id": 3
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `level` (string, optional): ログレベル "debug" | "info" | "warn" | "error"
- `limit` (number, optional): 取得件数 (デフォルト: 100)
- `since` (string, optional): 開始時刻 (ISO 8601形式)
- `filter` (string, optional): 正規表現フィルタ

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "logs": [
      {
        "timestamp": "2025-01-15T10:30:00Z",
        "level": "info",
        "message": "PacketProxy started successfully",
        "thread": "main",
        "class": "packetproxy.PacketProxy"
      }
    ],
    "total_count": 1500,
    "has_more": true
  },
  "id": 3
}
```

### 4. `get_config` - 設定情報取得

PacketProxyの設定情報をHTTP API (`http://localhost:32349/config`) 経由で取得します。PacketProxyHub互換の完全な設定形式で返されます。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_config",
    "arguments": {
      "categories": ["listenPorts", "servers"],
      "access_token": "your_access_token_here"
    }
  },
  "id": 4
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `categories` (array, optional): 取得するカテゴリ (空の場合は全て)
- `listenPorts`: リッスンポート設定
- `servers`: サーバー設定  
- `modifications`: 改変設定
- `sslPassThroughs`: SSL パススルー設定

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"listenPorts\":[{\"id\":1,\"enabled\":true,\"ca_name\":\"PacketProxy per-user CA\",\"port\":8080,\"type\":\"HTTP_PROXY\",\"server_id\":1}],\"servers\":[{\"id\":1,\"ip\":\"target.com\",\"port\":443,\"encoder\":\"HTTPS\",\"use_ssl\":true,\"resolved_by_dns\":false,\"resolved_by_dns6\":false,\"http_proxy\":false,\"comment\":\"\",\"specifiedByHostName\":false}]}"
      }
    ]
  },
  "id": 4
}
```

### 5. `update_config` - 設定変更

Update PacketProxy configuration settings with complete configuration object.
IMPORTANT: Requires a complete configuration object, not partial updates.

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "update_config",
    "arguments": {
      "config_json": {
        "listenPorts": [
          {
            "id": 1,
            "enabled": true,
            "ca_name": "PacketProxy per-user CA",
            "port": 8080,
            "type": "HTTP_PROXY",
            "server_id": 1
          }
        ],
        "servers": [
          {
            "id": 1,
            "ip": "target.com",
            "port": 443,
            "encoder": "HTTPS",
            "use_ssl": true,
            "resolved_by_dns": false,
            "resolved_by_dns6": false,
            "http_proxy": false,
            "comment": "",
            "specifiedByHostName": false
          }
        ],
        "modifications": [
          {
            "id": 1,
            "enabled": true,
            "server_id": 1,
            "direction": "CLIENT_REQUEST",
            "pattern": ".*",
            "method": "SIMPLE",
            "replaced": "X-Test: 1"
          }
        ],
        "sslPassThroughs": [
          {
            "id": 1,
            "enabled": true,
            "server_name": "secure.com",
            "listen_port": 443
          }
        ]
      },
      "backup": true,
      "suppress_dialog": false,
      "access_token": "your_access_token_here"
    }
  },
  "id": 5
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `config_json` (object, required): PacketProxyHub-compatible configuration JSON containing COMPLETE configuration object. Must include all required arrays: listenPorts, servers, modifications, sslPassThroughs (can be empty arrays). Partial configurations will cause null pointer errors. Recommended workflow: 1) Call get_config() first, 2) Modify specific fields in the returned object, 3) Pass the entire modified object here.
- `backup` (boolean, optional): 既存設定をバックアップ (デフォルト: true)
- `suppress_dialog` (boolean, optional): 確認ダイアログを非表示にする (デフォルト: false)

**重要な注意事項:**

**完全な設定オブジェクトが必要:**
- `config_json`は部分的な設定ではなく、**完全な設定オブジェクト**である必要があります
- 以下の配列は必須です（空配列でも可）：
- `listenPorts`: リッスンポート設定
- `servers`: サーバー設定
- `modifications`: 改変設定
- `sslPassThroughs`: SSL パススルー設定
- 部分的な設定を渡すとnull pointerエラーが発生します

**推奨ワークフロー:**
1. 最初に`get_config()`を呼び出して現在の完全な設定を取得
2. 取得した設定オブジェクトの特定のフィールドを変更
3. 変更した完全なオブジェクトを`update_config()`に渡す

**設定削除について:**
- `config_json`に含まれないIDの項目は自動的に削除されます
- 例: serversに`id:1`のみ含まれている場合、`id:2,3...`のサーバーは削除されます
- HTTP APIは既存設定を完全に置き換える方式で動作します

**ダイアログ制御について:**
- `suppress_dialog: false` (デフォルト): 設定上書き前に確認ダイアログを表示
- `suppress_dialog: true`: 確認ダイアログを表示せずに自動的に設定を上書き
- ダイアログが表示される場合、ユーザーが「はい」を選択した場合のみ設定が適用されます
- ダイアログで「いいえ」を選択した場合、HTTP 401エラーが返されます

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"success\": true, \"backup_created\": true, \"backup_info\": {\"backup_id\": \"backup_20250804_120000\", \"backup_path\": \"backup/backup_20250804_120000.json\", \"timestamp\": \"2025-08-04T12:00:00Z\"}, \"config_updated\": true}"
      }
    ]
  },
  "id": 5
}
```

### 6. `restore_config` - 設定バックアップ復元

指定したバックアップから設定を復元します。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "restore_config",
    "arguments": {
      "access_token": "your_access_token_here",
      "backup_id": "backup_20250115_103000",
      "suppress_dialog": false
    }
  },
  "id": 6
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `backup_id` (string, required): 復元するバックアップID
- `suppress_dialog` (boolean, optional): 確認ダイアログを非表示にする (デフォルト: false)

**ダイアログ制御について:**
- `suppress_dialog: false` (デフォルト): 設定復元前に確認ダイアログを表示
- `suppress_dialog: true`: 確認ダイアログを表示せずに自動的に設定を復元
- ダイアログが表示される場合、ユーザーが「はい」を選択した場合のみ設定が適用されます
- ダイアログで「いいえ」を選択した場合、HTTP 401エラーが返されます

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"success\": true, \"backup_id_restored\": \"backup_20250115_103000\", \"config_restored\": true}"
      }
    ]
  },
  "id": 6
}
```

### 7. `resend_packet` - パケット再送

パケットを再送します。パケット改変や連続送信に対応しています。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "resend_packet",
    "arguments": {
      "packet_id": 123,
      "count": 20,
      "interval_ms": 100,
      "modifications": [
        {
          "target": "request",
          "type": "regex_replace",
          "pattern": "sessionId=\\w+",
          "replacement": "sessionId=modified_{{index}}"
        },
        {
          "type": "header_add",
          "name": "X-Test-Counter",
          "value": "{{timestamp}}"
        }
      ],
      "async": false,
      "allow_duplicate_headers": false
    }
  },
  "id": 7
}
```

**パラメータ:**
- `packet_id` (number, required): 再送するパケットID
- `count` (number, optional): 送信回数 (デフォルト: 1)
- `interval_ms` (number, optional): 送信間隔(ms) (デフォルト: 0)
- `modifications` (array, optional): パケット改変設定
- `async` (boolean, optional): 非同期実行 (デフォルト: false)
- `allow_duplicate_headers` (boolean, optional): ヘッダー追加/変更時に重複を許可 (デフォルト: false)

**改変設定:**
- `target`: "request" | "response" | "both"
- `type`: "regex_replace" | "header_add" | "header_modify"
- `pattern`: 正規表現パターン (regex_replaceの場合)
- `replacement` / `value`: 置換文字列
- `name`: ヘッダー名 (header_add/header_modifyの場合)

**ヘッダー重複制御:**
- `allow_duplicate_headers=false` (デフォルト): 同名ヘッダーが存在する場合は既存を置換
- `allow_duplicate_headers=true`: 同名ヘッダーが存在していても新しいヘッダーを追加

**置換変数:**
- `{{index}}`: 送信順序 (1, 2, 3...)
- `{{timestamp}}`: Unix timestamp
- `{{random}}`: ランダム文字列(8文字)
- `{{uuid}}`: UUID v4
- `{{datetime}}`: ISO 8601形式日時

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "success": true,
    "sent_count": 20,
    "failed_count": 0,
    "job_id": "af2adff0-a35a-43ef-b653-cb47203727df",
    "execution_time_ms": 2100
  },
  "id": 7
}
```

### 8. `bulk_send` - 複数パケット一括送信

複数のパケットを一括で送信します。並列・順次送信モード、動的パラメータ、改変機能をサポートします。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "bulk_send",
    "arguments": {
      "access_token": "your_access_token_here",
      "packet_ids": [123, 124, 125],
      "mode": "sequential",
      "count": 2,
      "interval_ms": 500,
      "modifications": [
        {
          "type": "header_add",
          "name": "X-Test-Run",
          "value": "{{timestamp}}"
        }
      ],
      "regex_params": [
        {
          "pattern": "token=([a-zA-Z0-9]+)",
          "value_template": "token={{random}}-{{packet_index}}",
          "target": "request"
        }
      ],
      "allow_duplicate_headers": false,
      "async": false,
      "timeout_ms": 30000
    }
  },
  "id": 8
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `packet_ids` (array, required): 送信するパケットIDの配列 (1-100個)
- `mode` (string, optional): 送信モード "parallel" | "sequential" (デフォルト: "parallel")
- `count` (number, optional): 各パケットの送信回数 (デフォルト: 1, 最大: 1000)
- `interval_ms` (number, optional): 送信間隔(ms) (順次モードのみ, デフォルト: 0, 最大: 60000)
- `modifications` (array, optional): 全パケットに適用する改変設定 (resend_packetと同じ形式)
- `regex_params` (array, optional): 動的値置換パラメータ
- `allow_duplicate_headers` (boolean, optional): ヘッダー重複許可 (デフォルト: false)
- `async` (boolean, optional): 非同期実行 (デフォルト: false)
- `timeout_ms` (number, optional): 全体タイムアウト(ms) (デフォルト: 30000, 最大: 300000)

**regex_params設定:**
- `packet_index` (number, optional): 対象パケットインデックス (0ベース、省略時は全パケット)
- `pattern` (string, required): マッチする正規表現パターン
- `value_template` (string, required): 置換テンプレート (変数: {{packet_index}}, {{timestamp}}, {{random}}, {{uuid}})
- `target` (string, optional): 対象 "request" | "response" | "both" (デフォルト: "request")

**送信モード:**
- `parallel`: 全パケットを並列送信 (高速、interval_msは無視)
- `sequential`: パケットを順次送信 (制御された実行、regex_paramsによる値の引き継ぎ)

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "success": true,
    "mode": "sequential",
    "total_packets": 3,
    "total_count": 6,
    "sent_count": 5,
    "failed_count": 1,
    "execution_time_ms": 1250,
    "results": [
      {
        "original_packet_id": 123,
        "packet_index": 0,
        "success": true,
        "sent_count": 2,
        "failed_count": 0,
        "new_packet_ids": [145, 146],
        "error": null,
        "execution_time_ms": 245
      },
      {
        "original_packet_id": 124,
        "packet_index": 1,
        "success": false,
        "sent_count": 0,
        "failed_count": 2,
        "new_packet_ids": [],
        "error": "Connection timeout",
        "execution_time_ms": 5000
      }
    ],
    "regex_params_applied": [
      {
        "packet_index": 0,
        "pattern": "token=([a-zA-Z0-9]+)",
        "extracted_value": "abc123def",
        "applied_count": 1
      }
    ],
    "performance": {
      "packets_per_second": 4.0,
      "average_response_time_ms": 312,
      "concurrent_connections": 3
    },
    "job_id": "bulk_send_20250804_120030_abc123"
  },
  "id": 8
}
```

**非同期実行レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "async": true,
    "job_id": "bulk_send_20250804_120030_abc123",
    "status": "started",
    "total_packets": 50,
    "estimated_duration_ms": 30000,
    "monitor_url": "/mcp/bulk_send/status/bulk_send_20250804_120030_abc123"
  },
  "id": 8
}
```

### 9. `call_vulcheck_helper` - VulCheck脆弱性テストヘルパー

指定されたパケットにVulCheckテストケースを適用して、自動的にペイロードを生成し、指定された位置に注入してバッチ送信を実行します。

**重要な制限事項**: 現在、NumberとJWTの脆弱性タイプのみをサポートしています。その他の脆弱性診断については、`bulk_send`や`resend_packet`ツールを使用してください。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "call_vulcheck_helper",
    "arguments": {
      "access_token": "your_access_token_here",
      "packet_id": 123,
      "vulcheck_type": "Number",
      "target_locations": [
        {
          "pattern": "userId=\\d+",
          "replacement": "userId=$1",
          "description": "User ID parameter"
        },
        {
          "pattern": "amount=[0-9.]+",
          "replacement": "amount=$1",
          "description": "Amount field"
        }
      ],
      "interval_ms": 100,
      "mode": "sequential",
      "max_payloads": 50,
      "timeout_ms": 300000
    }
  },
  "id": 9
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `packet_id` (number, required): VulCheckテストのベースとして使用するパケットID
- `vulcheck_type` (string, required): 実行するVulCheckの種類 (Number, JWT等)。'list'を指定すると利用可能なタイプ一覧を取得
- `target_locations` (array, required): VulCheckペイロードを注入するパケット内の対象位置の配列。正規表現パターンまたは位置範囲で指定可能
- **正規表現アプローチ (推奨):**
- `pattern` (string, required): マッチ対象の正規表現パターン (例: `"sessionId=\\w+"`)
- `replacement` (string, optional): 置換テンプレート。省略時はマッチ全体を置換。`$1`でペイロードを表す (例: `"sessionId=$1"`)
- `description` (string, optional): この対象位置の説明
- **位置範囲アプローチ (後方互換性のため保持):**
- `start` (number, required): 対象位置の開始位置
- `end` (number, required): 対象位置の終了位置  
- `description` (string, optional): この対象位置の説明
- `interval_ms` (number, optional): パケット送信間隔(ms) (デフォルト: 100)
- `mode` (string, optional): 実行モード "sequential" | "parallel" (デフォルト: "sequential")
- `max_payloads` (number, optional): 位置ごとの最大ペイロード生成数 (デフォルト: 50, 最大: 1000)
- `timeout_ms` (number, optional): 全体操作タイムアウト(ms) (デフォルト: 300000 - 5分)

**実行モード:**
- `sequential`: ペイロードを順次送信 (間隔制御あり)
- `parallel`: 全ペイロードを並列送信 (高速、interval_msは無視)

**利用可能なVulCheckタイプ取得:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "call_vulcheck_helper",
    "arguments": {
      "access_token": "your_access_token_here",
      "packet_id": 123,
      "vulcheck_type": "list"
    }
  },
  "id": 10
}
```

**VulCheckタイプ一覧レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "available_vulcheck_types": "Number, JWT",
    "vulcheck_types": [
      {
        "name": "Number",
        "description": "VulCheck tests for Number vulnerabilities",
        "generators": [
          {"name": "NegativeNumber", "generate_on_start": true},
          {"name": "Zero", "generate_on_start": true},
          {"name": "Decimals", "generate_on_start": true},
          {"name": "IntegerOverflow", "generate_on_start": true}
        ],
        "generator_count": 9
      },
      {
        "name": "JWT",
        "description": "VulCheck tests for JWT vulnerabilities", 
        "generators": [
          {"name": "JWTPayloadModified", "generate_on_start": false},
          {"name": "JWTHeaderAlgNone", "generate_on_start": false}
        ],
        "generator_count": 8
      }
    ],
    "total_types": 2
  },
  "id": 10
}
```

**実行結果レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "success": true,
    "vulcheck_type": "Number",
    "mode": "sequential",
    "total_locations": 2,
    "total_payloads_generated": 18,
    "total_packets_sent": 16,
    "total_failed": 2,
    "execution_time_ms": 2400,
    "location_results": [
      {
        "start": 45,
        "end": 50,
        "description": "User ID parameter (match 1)",
        "payloads_generated": 9,
        "packets_sent": 8,
        "packets_failed": 1,
        "execution_time_ms": 1200,
        "generated_payloads": [
          "-1", "0", "0.1", "2147483647", "2147483648",
          "-2147483649", "9223372036854775807", "9223372036854775808",
          "-9223372036854775809"
        ]
      },
      {
        "start": 78,
        "end": 85,
        "description": "Amount field (match 1)",
        "payloads_generated": 9,
        "packets_sent": 8,
        "packets_failed": 1,
        "execution_time_ms": 1200,
        "generated_payloads": [
          "-1", "0", "0.1", "2147483647", "2147483648",
          "-2147483649", "9223372036854775807", "9223372036854775808",
          "-9223372036854775809"
        ]
      }
    ],
    "performance": {
      "average_interval_ms": 100,
      "payloads_per_second": 6.67,
      "success_rate_percent": 88.89
    },
    "job_id": "vulcheck_20250804_120030_def456"
  },
  "id": 9
}
```

**使用例:**

```bash
# 利用可能なVulCheckタイプを確認
curl -X POST http://localhost:32349/mcp/tools/call \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_access_token" \
  -d '{
    "name": "call_vulcheck_helper",
    "arguments": {
      "packet_id": 123,
      "vulcheck_type": "list"
    }
  }'

# Number VulCheckを実行
curl -X POST http://localhost:32349/mcp/tools/call \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_access_token" \
  -d '{
    "name": "call_vulcheck_helper", 
    "arguments": {
      "packet_id": 123,
      "vulcheck_type": "Number",
      "target_locations": [
        {"pattern": "userId=\\d+", "replacement": "userId=$1", "description": "User ID"}
      ],
      "mode": "sequential",
      "interval_ms": 200,
      "max_payloads": 10
    }
  }'
```

### 10. `get_job_status` - ジョブ状況取得

send系ツール（resend_packet/bulk_send/call_vulcheck_helper）で作成されたジョブの実行状況を取得します。

**リクエスト:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_job_status",
    "arguments": {
      "job_id": "af2adff0-a35a-43ef-b653-cb47203727df"
    }
  },
  "id": 10
}
```

**パラメータ:**
- `job_id` (string, optional): 取得するジョブのID。指定しない場合は全ジョブの概要を返す

**特定ジョブの詳細レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "job_id": "af2adff0-a35a-43ef-b653-cb47203727df",
    "total_requests": 5,
    "requests_sent": 5,
    "responses_received": 3,
    "status": "receiving_responses",
    "requests": [
      {
        "temporary_id": "temp_001",
        "has_request": true,
        "has_response": true,
        "request_packet_id": 124,
        "response_packet_id": 125
      },
      {
        "temporary_id": "temp_002",
        "has_request": true,
        "has_response": false,
        "request_packet_id": 126
      }
    ]
  },
  "id": 10
}
```

**全ジョブ概要レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "total_jobs": 3,
    "jobs": [
      {
        "job_id": "af2adff0-a35a-43ef-b653-cb47203727df",
        "total_requests": 5,
        "requests_sent": 5,
        "responses_received": 3,
        "status": "receiving_responses"
      },
      {
        "job_id": "bulk_send_20250804_120030_abc123",
        "total_requests": 10,
        "requests_sent": 10,
        "responses_received": 10,
        "status": "completed"
      }
    ]
  },
  "id": 10
}
```

**ジョブ状態:**
- `created`: ジョブは作成されたがリクエストはまだ送信されていない
- `requests_sent`: 全リクエストが送信済み、レスポンス待ち
- `receiving_responses`: 一部のレスポンスを受信中
- `completed`: 全リクエスト・レスポンスが完了

**ジョブの概念:**

PacketProxyのジョブシステムは、send系ツールで送信されたパケットの追跡を可能にします：

- **job_id**: 各send系ツール実行時に生成されるUUID
- **temporary_id**: ジョブ内の各リクエスト/レスポンスペアを識別するUUID
- **パケット関連付け**: 送信されたパケットと受信されたレスポンスがtemporary_idで関連付けられる
- **状況追跡**: データベースに保存されたパケット履歴からジョブの進行状況をリアルタイムで取得

## フィルタ構文仕様

PacketProxyのFilterTextParserに準拠した構文を使用します。

### 利用可能カラム

|     カラム名      |    型     |       説明        |
|---------------|----------|-----------------|
| `id`          | integer  | パケットID          |
| `request`     | string   | リクエスト内容         |
| `response`    | string   | レスポンス内容         |
| `length`      | integer  | パケットサイズ         |
| `client_ip`   | string   | クライアントIP        |
| `client_port` | integer  | クライアントポート       |
| `server_ip`   | string   | サーバーIP          |
| `server_port` | integer  | サーバーポート         |
| `time`        | datetime | タイムスタンプ         |
| `resend`      | boolean  | 再送フラグ           |
| `modified`    | boolean  | 改変フラグ           |
| `type`        | string   | プロトコルタイプ        |
| `encode`      | string   | エンコーダ種別         |
| `alpn`        | string   | ALPN情報          |
| `group`       | integer  | グループID          |
| `full_text`   | string   | 全文検索 (大文字小文字区別) |
| `full_text_i` | string   | 全文検索 (大文字小文字無視) |

### 演算子

|  演算子   |    説明    |                  例                  |
|--------|----------|-------------------------------------|
| `==`   | 等しい      | `method == GET`                     |
| `!=`   | 等しくない    | `status != 200`                     |
| `>=`   | 以上       | `length >= 1000`                    |
| `<=`   | 以下       | `status <= 299`                     |
| `=~`   | 正規表現マッチ  | `url =~ /api/v[0-9]+/`              |
| `!~`   | 正規表現非マッチ | `url !~ /static/`                   |
| `&&`   | AND演算    | `method == POST && status >= 400`   |
| `\|\|` | OR演算     | `method == GET \|\| method == POST` |

### フィルタ例

```
# HTTP エラー
status >= 400 && status <= 599

# 大きなリクエスト
length > 10000

# API コール
url =~ /api/ && (method == GET || method == POST)

# 認証関連
full_text_i =~ authorization

# WebSocket トラフィック
type == WebSocket

# 複合条件
method == POST && url =~ /login && status == 401

# 並び順の例
# 最新順: order: "time desc"
# サイズ順: order: "length asc" 
# エラー優先: order: "status desc"
# ID逆順: order: "id desc"
```

## HTTP REST API (補完)

MCP以外の方法でもアクセス可能なHTTP REST APIを提供します。

### エンドポイント

```
GET  /mcp/tools                          # ツール一覧
GET  /mcp/history?filter=...&limit=100&order=time+desc   # パケット履歴
GET  /mcp/packet/{id}                    # パケット詳細
GET  /mcp/configs                        # 設定一覧
PUT  /mcp/configs                        # 設定更新
POST /mcp/resend/{packet_id}             # パケット再送
POST /mcp/bulk_send                      # 複数パケット一括送信
POST /mcp/call_vulcheck_helper           # VulCheck脆弱性テストヘルパー
GET  /mcp/job_status?job_id=...          # ジョブ状況取得
GET  /mcp/logs?level=info                # ログ取得
POST /mcp/restore/{backup_id}             # バックアップ復元
```

### HTTP ヘッダー制御

設定更新API (`POST /config`) では以下の特別なHTTPヘッダーをサポートします：

- `X-Suppress-Dialog: true`: 確認ダイアログを非表示にして自動的に設定を上書き
- `X-Suppress-Dialog: false` (デフォルト): 確認ダイアログを表示

### 認証

HTTP APIはアクセストークンによる認証を使用します。

```http
Authorization: Bearer <access_token>
```

## エラーハンドリング

### MCP標準エラー

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid params",
    "data": {
      "details": "packet_id is required"
    }
  },
  "id": 1
}
```

### カスタムエラーコード

|  コード   |              説明              |
|--------|------------------------------|
| -32001 | PacketProxy connection error |
| -32002 | Invalid filter syntax        |
| -32003 | Packet not found             |
| -32004 | Configuration error          |
| -32005 | Permission denied            |

## セキュリティ考慮事項

1. **アクセス制御**: 設定変更操作は適切な権限を要求
2. **入力検証**: すべての入力パラメータを検証
3. **ログ記録**: 重要な操作はログに記録
4. **レート制限**: パケット再送などの高負荷操作に制限
5. **設定バックアップ**: 重要な設定変更前に自動バックアップ

## パフォーマンス考慮事項

1. **フィルタ最適化**: 複雑なフィルタは性能警告を表示
2. **ページング**: 大量データは適切にページング
3. **キャッシュ**: 頻繁にアクセスされるデータはキャッシュ
4. **非同期処理**: 時間のかかる操作は非同期実行をサポート

## 環境変数

### MCP HTTP Bridge 環境変数

MCP HTTP Bridgeは以下の環境変数をサポートします：

#### `PACKETPROXY_ACCESS_TOKEN`

- **説明**: PacketProxyのアクセストークン
- **必須**: 推奨 (手動指定の代替)
- **形式**: 文字列
- **例**: `export PACKETPROXY_ACCESS_TOKEN="abc123def456"`
- **動作**: 設定時、すべてのMCPツール呼び出しに自動的にアクセストークンが追加されます

#### `MCP_DEBUG`

- **説明**: デバッグログ出力制御
- **必須**: オプション
- **形式**: `"true"` または `"false"`
- **デフォルト**: `"false"`
- **例**: `export MCP_DEBUG="true"`
- **動作**:
  - `"true"`: デバッグメッセージをstderrに出力
  - `"false"`: デバッグメッセージを出力しない (JSON-RPC通信を汚染しない)

#### 使用例

```bash
# 基本設定
export PACKETPROXY_ACCESS_TOKEN="your_access_token_here"

# デバッグ有効化
export MCP_DEBUG="true"
export PACKETPROXY_ACCESS_TOKEN="your_access_token_here"

# MCP HTTP Bridge起動
node /path/to/mcp-http-bridge.js
```

## 実装詳細

### ディレクトリ構成

```
src/main/java/core/packetproxy/extensions/mcp/
├── MCPServerExtension.java       # Extension基底クラス継承
├── MCPServer.java               # MCP JSONRPCサーバー実装
├── tools/
│   ├── HistoryTool.java        # History情報取得
│   ├── SettingTool.java        # 設定情報取得・変更
│   ├── LogTool.java            # ログ情報取得
│   ├── ResendTool.java         # パケット再送
│   └── FilterTool.java         # フィルタ関連操作
├── MCPToolRegistry.java        # ツール登録・管理
└── backup/
    └── ConfigBackupManager.java # 設定バックアップ管理
```

### 統合ポイント

- **Extension管理**: GUIOptionExtensionsで有効/無効切り替え
- **データアクセス**: 既存のPackets, Configs, Filters等のAPIを活用
- **パケット操作**: ResendControllerを使用した再送機能
- **設定管理**: ConfigIOを使用したPacketProxyHub互換性

## バージョン履歴

| バージョン |     日付     | 変更内容 |
|-------|------------|------|
| 1.0.0 | 2025-08-04 | 初版作成 |

