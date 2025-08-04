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
export PACKET_PROXY_ACCESS_TOKEN="your_access_token_here"
```

この設定により、各ツール呼び出し時に手動で`access_token`パラメータを指定する必要がなくなります。

### 認証エラーの場合

- アクセストークンが未設定: PacketProxyでconfig sharingを有効にしてください
- アクセストークンが無効: Settings画面で正しいトークンを確認してください
- アクセストークンが空: 必須パラメータのため、必ず指定してください
- 環境変数が設定されていない場合: `PACKET_PROXY_ACCESS_TOKEN`環境変数を確認してください

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

特定のパケットの詳細情報を取得します。

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
      "include_body": true
    }
  },
  "id": 2
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `packet_id` (number, required): パケットID
- `include_body` (boolean, optional): リクエスト/レスポンスボディを含める (デフォルト: false)

**レスポンス:**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "id": 123,
    "method": "GET",
    "url": "/api/users",
    "status": 200,
    "headers": {
      "request": [
        {"name": "Host", "value": "api.example.com"},
        {"name": "User-Agent", "value": "Mozilla/5.0..."}
      ],
      "response": [
        {"name": "Content-Type", "value": "application/json"},
        {"name": "Content-Length", "value": "1024"}
      ]
    },
    "body": {
      "request": "",
      "response": "{\"users\": [...]}"
    },
    "timing": {
      "timestamp": "2025-01-15T10:30:00Z",
      "duration_ms": 245
    }
  },
  "id": 2
}
```

### 3. `get_config` - 設定情報取得

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
  "id": 3
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
  "id": 3
}
```

### 4. `update_config` - 設定変更

PacketProxyの設定をHTTP API (`http://localhost:32349/config`) 経由で変更します。PacketProxyHub互換の形式を使用し、指定されたIDが含まれない項目は自動的に削除されます。

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
      "access_token": "your_access_token_here"
    }
  },
  "id": 4
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `config_json` (object, required): PacketProxyHub互換の設定JSON（完全な形式）
- `backup` (boolean, optional): 既存設定をバックアップ (デフォルト: true)

**設定削除について:**
- `config_json`に含まれないIDの項目は自動的に削除されます
- 例: serversに`id:1`のみ含まれている場合、`id:2,3...`のサーバーは削除されます
- HTTP APIは既存設定を完全に置き換える方式で動作します

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
  "id": 4
}
```

### 5. `resend_packet` - パケット再送

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
  "id": 5
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
    "packet_ids": [124, 125, 126],
    "execution_time_ms": 2100
  },
  "id": 5
}
```

### 6. `get_logs` - ログ取得

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
  "id": 6
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
  "id": 6
}
```

### 7. `restore_config` - 設定バックアップ復元

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
      "backup_id": "backup_20250115_103000"
    }
  },
  "id": 7
}
```

**パラメータ:**
- `access_token` (string, required): PacketProxy設定のアクセストークン
- `backup_id` (string, required): 復元するバックアップID

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
  "id": 7
}
```

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
GET  /mcp/logs?level=info                # ログ取得
POST /mcp/restore/{backup_id}             # バックアップ復元
```

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

#### `PACKET_PROXY_ACCESS_TOKEN`

- **説明**: PacketProxyのアクセストークン
- **必須**: 推奨 (手動指定の代替)
- **形式**: 文字列
- **例**: `export PACKET_PROXY_ACCESS_TOKEN="abc123def456"`
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
export PACKET_PROXY_ACCESS_TOKEN="your_access_token_here"

# デバッグ有効化
export MCP_DEBUG="true"
export PACKET_PROXY_ACCESS_TOKEN="your_access_token_here"

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

