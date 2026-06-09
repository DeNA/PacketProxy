# PacketProxy CLI リファレンス

PacketProxy は GUI のほか、CLI サブコマンドとして `server` / `encode` / `decode` / `encoders` を提供します。GUI なしで CI や自動化スクリプトからプロキシを操作できます。

---

## 目次

1. [共通オプション](#共通オプション)
2. [packetproxy server](#packetproxy-server)
3. [packetproxy encode / decode](#packetproxy-encode--decode)
4. [packetproxy encoders](#packetproxy-encoders)
5. [管理 API](#管理-api)
   - [認証](#認証)
   - [サーバー状態](#サーバー状態)
   - [パケット履歴](#パケット履歴)
   - [再送・バルク送信](#再送バルク送信)
   - [VulCheck](#vulcheck)
   - [設定 CRUD](#設定-crud)
   - [データベースダウンロード](#データベースダウンロード)

---

## 共通オプション

```
-h, --help      ヘルプを表示
-V, --version   バージョンを表示
```

---

## packetproxy server

GUI なしでプロキシサーバーをフォアグラウンドで起動します。SIGINT（Ctrl+C）または SIGTERM で停止します。ログは stderr に出力されます。

### 構文

```
packetproxy server [オプション]
```

### オプション

|          オプション          |                                    説明                                     |
|-------------------------|---------------------------------------------------------------------------|
| `-c`, `--config <path>` | 設定 JSON ファイルのパス。指定すると SQLite の設定を JSON 内容で置き換えて起動。省略時は既存の SQLite をそのまま使用。 |
| `--no-log`              | ログ出力を抑制                                                                   |
| `--api-port <port>`     | 管理 API を起動するポート番号（省略時は API 無効）                                            |
| `--api-key <token>`     | API 認証トークン。全リクエストに `Authorization: Bearer <token>` が必要になる。省略時は認証なし。       |

### 設定の読み込み優先順位

```
--config 指定あり → JSON を DB に適用してプロキシ起動
--config 省略     → ~/.packetproxy/db/resources.sqlite3 をそのまま使用
                   （GUI で設定済みの内容がそのまま動く）
```

### 使用例

```bash
# GUI で設定した内容をそのまま CLI で起動
packetproxy server

# 設定 JSON をインポートして起動
packetproxy server --config settings.json

# 管理 API を有効化して起動（認証あり）
packetproxy server --api-port 8888 --api-key mytoken

# CI 向け：ログ抑制 + バックグラウンド実行
packetproxy server --config ci-settings.json --no-log &
```

### 設定 JSON のエクスポート

GUI の「File → Export Settings」または管理 API の `GET /api/config` で取得できます。

---

## packetproxy encode / decode

stdin のデータを指定エンコーダーで変換して stdout に出力します。スクリプト組み込みやパイプ処理に使用します。

### 構文

```
packetproxy encode -e <encoder> [オプション]
packetproxy decode -e <encoder> [オプション]
```

### オプション

|          オプション           |                     説明                      |
|--------------------------|---------------------------------------------|
| `-e`, `--encoder <name>` | エンコーダー名（必須）。一覧は `packetproxy encoders` で確認。 |
| `--response`             | サーバーレスポンス方向で変換。省略時はクライアントリクエスト方向。           |
| `--text`                 | 出力を UTF-8 テキストとして扱い、末尾に改行がなければ追加する。         |
| `--in <file>`            | 入力ファイル。省略時は stdin。                          |
| `--out <file>`           | 出力ファイル。省略時は stdout。                         |

### 使用例

```bash
# HTTP/2 リクエストをデコード（stdin → stdout）
cat request.bin | packetproxy decode -e HTTP2

# gRPC レスポンスをデコードしてテキスト表示
cat response.bin | packetproxy decode -e gRPC --response --text

# MessagePack をエンコードしてファイルに保存
packetproxy encode -e MessagePack --in plain.json --out encoded.bin

# パイプを繋いで変換チェーン
cat data.bin | packetproxy decode -e CBOR --text | jq '.id'
```

---

## packetproxy encoders

利用可能なエンコーダー名の一覧を表示します。

### 構文

```
packetproxy encoders
```

### 出力例

```
AmazonLexV2
CBOR
FireBase
Firestore
HTTP1
HTTP2
HTTPS
MessagePack
MQTT
ProtocolBuffers
WebSocket
gRPC
...
```

---

## 管理 API

`packetproxy server --api-port <port>` で起動すると、`http://127.0.0.1:<port>/api/` で REST API が利用可能になります。

すべてのレスポンスは `Content-Type: application/json` です（`GET /api/db` を除く）。

### 認証

`--api-key` を指定している場合、OPTIONS 以外の全リクエストに以下のヘッダーが必要です。

```
Authorization: Bearer <token>
```

以降の例では認証ありの場合を示します（`-H "Authorization: Bearer mytoken"`）。

---

### サーバー状態

#### GET /api/status — サーバー状態取得

```
GET /api/status
```

```bash
curl -H "Authorization: Bearer mytoken" http://127.0.0.1:8888/api/status
```

|  `status`  |                 説明                 |
|------------|------------------------------------|
| `STARTING` | コンポーネント初期化中（EncoderManager 等のロード中） |
| `READY`    | 全コンポーネント初期化済み、プロキシ稼働中              |
| `STOPPING` | シャットダウン処理中（SIGINT / SIGTERM 受信後）   |

`readyAt` / `uptimeSec` は `READY` または `STOPPING` 状態でのみ含まれます。

レスポンス例（`STARTING`）：

```json
{
  "status": "STARTING",
  "startedAt": 1749456000000
}
```

レスポンス例（`READY`）：

```json
{
  "status": "READY",
  "startedAt": 1749456000000,
  "readyAt":   1749456011000,
  "uptimeSec": 45
}
```

**起動待ちポーリングの例：**

```bash
until [ "$(curl -s -H "Authorization: Bearer mytoken" http://127.0.0.1:8888/api/status | jq -r '.status')" = "READY" ]; do
  echo "waiting for server to be ready..."
  sleep 2
done
echo "server is ready"
```

---

### パケット履歴

#### GET /api/packets — 一覧取得

```
GET /api/packets[?filter=<式>&offset=0&limit=100]
```

| クエリパラメータ |    デフォルト     |                     説明                     |
|----------|--------------|--------------------------------------------|
| `filter` | なし           | フィルター式（後述）                                 |
| `offset` | 0            | 取得開始位置                                     |
| `limit`  | 100（最大 1000） | 取得件数                                       |
| `q`      | なし           | フルテキスト検索（レガシー。`filter=full_text==<q>` と等価） |

データフィールド（`decoded_data` 等）は含みません。詳細は `GET /api/packets/{id}` を使ってください。

##### フィルター式の文法

GUI の FilterTextParser と同一の文法を使用します。

```
<式>         ::= <OR式>
<OR式>       ::= <AND式> | <AND式> '||' <OR式>
<AND式>      ::= <条件> | <条件> '&&' <AND式>
<条件>       ::= <カラム> <演算子> <値> | '(' <式> ')'
<演算子>     ::= '==' | '=~' | '!=' | '!~' | '>=' | '<='
```

**利用可能なカラム:**

|     カラム名      |    対応する DB フィールド     |      型       |        備考        |
|---------------|----------------------|--------------|------------------|
| `id`          | id                   | 整数           |                  |
| `client_ip`   | client_ip            | 文字列          | LIKE 部分一致        |
| `client_port` | client_port          | 整数           |                  |
| `server_ip`   | server_ip            | 文字列          | LIKE 部分一致        |
| `server_port` | server_port          | 整数           |                  |
| `time`        | date                 | 整数（epoch ms） |                  |
| `resend`      | resend               | boolean      | `true`/`false`   |
| `modified`    | modified             | boolean      | `true`/`false`   |
| `type`        | content_type         | 文字列          | LIKE 部分一致        |
| `encode`      | encoder_name         | 文字列          | LIKE 部分一致        |
| `alpn`        | alpn                 | 文字列          | LIKE 部分一致        |
| `group`       | group                | 整数           |                  |
| `length`      | LENGTH(decoded_data) | 整数           |                  |
| `full_text`   | decoded_data GLOB    | 文字列          | case-sensitive   |
| `full_text_i` | decoded_data LIKE    | 文字列          | case-insensitive |
| `request`     | decoded_data LIKE    | 文字列          | ベストエフォート         |
| `response`    | decoded_data LIKE    | 文字列          | ベストエフォート         |

> **注意**: 文字列カラムの `==`/`=~` は LIKE `'%値%'` で評価されます（正規表現は非サポート）。

```bash
# 最新 100 件
curl -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/packets

# ポート 443 に対する HTTP2 パケット
curl -H "Authorization: Bearer mytoken" \
  "http://127.0.0.1:8888/api/packets?filter=server_port==443%26%26encode==HTTP2"

# 改変済みパケット
curl -H "Authorization: Bearer mytoken" \
  "http://127.0.0.1:8888/api/packets?filter=modified==true"

# 特定サーバーへの通信
curl -H "Authorization: Bearer mytoken" \
  "http://127.0.0.1:8888/api/packets?filter=server_ip==example.com"

# decoded_data に "login" を含む（case-insensitive）
curl -H "Authorization: Bearer mytoken" \
  "http://127.0.0.1:8888/api/packets?filter=full_text_i==login"

# OR 条件: HTTP2 または gRPC
curl -H "Authorization: Bearer mytoken" \
  "http://127.0.0.1:8888/api/packets?filter=encode==HTTP2%7C%7Cencode==gRPC"

# 複合条件: ポート 443、改変済み、1000 bytes 以上
curl -H "Authorization: Bearer mytoken" \
  "http://127.0.0.1:8888/api/packets?filter=server_port==443%26%26modified==true%26%26length>=1000"

# ページング（URL エンコード済み例はシェルから直接送る場合）
DATA=$(python3 -c "import urllib.parse; print(urllib.parse.quote('server_port==443&&encode==HTTP2'))")
curl -H "Authorization: Bearer mytoken" \
  "http://127.0.0.1:8888/api/packets?filter=$DATA&offset=50&limit=50"
```

レスポンス例：

```json
{
  "data": [
    {
      "id": 42,
      "direction": "CLIENT",
      "listenPort": 8080,
      "clientIp": "127.0.0.1",
      "serverIp": "192.168.1.1",
      "serverPort": 443,
      "serverName": "example.com",
      "encoderName": "HTTP2",
      "modified": false,
      "resend": false,
      "date": 1749456000000,
      "color": null
    }
  ],
  "offset": 0,
  "limit": 100
}
```

#### GET /api/packets/{id} — 詳細取得

```
GET /api/packets/{id}
```

`decoded_data` / `modified_data` / `sent_data` / `received_data` を Base64 エンコードして返します。

```bash
curl -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/packets/42
```

レスポンス例：

```json
{
  "data": {
    "id": 42,
    "direction": "CLIENT",
    "listenPort": 8080,
    "clientIp": "127.0.0.1",
    "clientPort": 54321,
    "serverIp": "192.168.1.1",
    "serverPort": 443,
    "serverName": "example.com",
    "useSsl": true,
    "encoderName": "HTTP2",
    "alpn": "h2",
    "modified": false,
    "resend": false,
    "date": 1749456000000,
    "conn": 3,
    "group": 100,
    "color": null,
    "decodedData": "UFJJIFN...",
    "modifiedData": null,
    "sentData": "UFJJIFN...",
    "receivedData": null
  }
}
```

`decodedData` は Base64 なので、元のバイナリに戻すには以下のようにします。

```bash
curl -s -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/packets/42 \
  | jq -r '.data.decodedData' \
  | base64 -d > decoded.bin
```

---

### 再送・バルク送信

#### POST /api/packets/{id}/resend — 1 回再送

```
POST /api/packets/{id}/resend
Content-Type: application/json

{"data": "<base64>"}   // 省略可
```

`data` を省略した場合、パケットの `modified_data`（なければ `decoded_data`）を使用します。送信は非同期で開始され、即座にレスポンスを返します。

```bash
# modified_data でそのまま再送
curl -s -X POST \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d '{}' \
  http://127.0.0.1:8888/api/packets/42/resend

# 内容を書き換えて送信（Base64 エンコード）
DATA=$(echo -n "GET /modified HTTP/1.1\r\n..." | base64)
curl -s -X POST \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d "{\"data\": \"$DATA\"}" \
  http://127.0.0.1:8888/api/packets/42/resend
```

レスポンス：

```json
{"status": "accepted"}
```

#### POST /api/packets/{id}/bulk-send — バルク送信

```
POST /api/packets/{id}/bulk-send
Content-Type: application/json

{
  "packets": [
    {"data": "<base64>"},
    {"data": "<base64>"},
    ...
  ]
}
```

指定パケットの接続情報（サーバーアドレス、エンコーダー等）を使い、`packets` 配列の内容を順番に送信します。

```bash
# ID=1 と ID=2 のパケットデータをそれぞれ取得して一括送信する例
DATA1=$(curl -s -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/packets/1 | jq -r '.data.decodedData')
DATA2=$(curl -s -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/packets/2 | jq -r '.data.decodedData')

curl -s -X POST \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d "{\"packets\": [{\"data\": \"$DATA1\"}, {\"data\": \"$DATA2\"}]}" \
  http://127.0.0.1:8888/api/packets/42/bulk-send
```

レスポンス：

```json
{"status": "accepted", "count": 2}
```

---

### VulCheck

VulCheck は、パケット内の特定バイト範囲を脆弱性テスト用ペイロードで置換して自動送信する機能です。

#### GET /api/vulcheckers — チェッカー一覧

```
GET /api/vulcheckers
```

```bash
curl -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/vulcheckers
```

レスポンス例：

```json
{
  "data": [
    {
      "name": "Number",
      "generators": [
        "NegativeNumber",
        "Zero",
        "Decimals",
        "IntegerOverflow",
        "IntegerUnderflow",
        "LongOverflow",
        "LongUnderflow"
      ]
    },
    {
      "name": "JWT",
      "generators": ["AlgNone", "WeakHMAC", "RS256ToHS256"]
    }
  ]
}
```

#### POST /api/vulcheckers/{name}/run — VulCheck 実行

```
POST /api/vulcheckers/{name}/run
Content-Type: application/json

{
  "packet_id":   42,
  "range_start": 10,
  "range_end":   20
}
```

|     フィールド     |              説明               |
|---------------|-------------------------------|
| `packet_id`   | ベースにするパケットの ID                |
| `range_start` | 置換対象のバイト開始位置（0 始まり、inclusive） |
| `range_end`   | 置換対象のバイト終了位置（exclusive）       |

指定チェッカーの全 Generator がペイロードを生成し、`range_start`〜`range_end` を置換した OneShotPacket を順番に送信します。

**ワークフロー例：**

1. `GET /api/packets/42` でパケット内容を確認
2. `decodedData` を Base64 デコードして対象パラメータのバイト位置を特定
3. VulCheck を実行

```bash
# Number チェッカーでパケット 42 のバイト 10〜20 をテスト
curl -s -X POST \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d '{"packet_id": 42, "range_start": 10, "range_end": 20}' \
  http://127.0.0.1:8888/api/vulcheckers/Number/run
```

レスポンス：

```json
{"status": "accepted", "sent": 7}
```

送信結果はプロキシの通信履歴（`GET /api/packets`）に残ります。

---

### 設定 CRUD

#### GET /api/config — 設定全取得

```
GET /api/config
```

`packetproxy server --config` で使用するものと同じ JSON フォーマットで返します。

```bash
curl -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/config > settings.json
```

#### PUT /api/config — 設定全置換

```
PUT /api/config
Content-Type: application/json
```

既存の設定（ListenPort / Server / Modification / SSLPassThrough）をすべて削除して、ボディの内容で置き換えます。`GET /api/config` の返却値と同じフォーマットを使用します。

```bash
curl -s -X PUT \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d @settings.json \
  http://127.0.0.1:8888/api/config
```

#### ListenPort CRUD

```
GET    /api/listenports          一覧
POST   /api/listenports          作成
PUT    /api/listenports/{id}     更新
DELETE /api/listenports/{id}     削除
```

```bash
# 一覧
curl -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/listenports

# 作成（ポート 8080、HTTP_PROXY タイプ、サーバー ID=1）
curl -s -X POST \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d '{"port": 8080, "type": "HTTP_PROXY", "serverId": 1, "enabled": true}' \
  http://127.0.0.1:8888/api/listenports

# 有効/無効を切り替え
curl -s -X PUT \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d '{"port": 8080, "type": "HTTP_PROXY", "serverId": 1, "enabled": false}' \
  http://127.0.0.1:8888/api/listenports/3

# 削除
curl -s -X DELETE \
  -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/listenports/3
```

#### Server CRUD

```
GET    /api/servers          一覧
POST   /api/servers          作成
PUT    /api/servers/{id}     更新
DELETE /api/servers/{id}     削除
```

```bash
# 一覧
curl -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/servers

# 作成
curl -s -X POST \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d '{"ip": "example.com", "port": 443, "encoder": "HTTP2", "useSsl": true}' \
  http://127.0.0.1:8888/api/servers

# 削除
curl -s -X DELETE \
  -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/servers/2
```

#### Modification CRUD

```
GET    /api/modifications          一覧
POST   /api/modifications          作成
PUT    /api/modifications/{id}     更新
DELETE /api/modifications/{id}     削除
```

```bash
# 一覧
curl -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/modifications

# 作成（リクエスト中の "staging" を "production" に置換）
curl -s -X POST \
  -H "Authorization: Bearer mytoken" \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": -1,
    "direction": "CLIENT_REQUEST",
    "method": "SIMPLE",
    "pattern": "staging",
    "replaced": "production",
    "enabled": true
  }' \
  http://127.0.0.1:8888/api/modifications
```

---

### データベースダウンロード

#### GET /api/db — SQLite データベースファイルのダウンロード

通信ログを含む SQLite データベース（`resources.sqlite3`）をダウンロードします。オフライン分析や別環境への移植に使用します。

内部では DB 書き込みを一時停止してからファイルを丸ごとコピーし、完了後に再開するため一貫性のあるバックアップが取得できます。

```
GET /api/db
```

レスポンスは `application/octet-stream`（`Content-Disposition: attachment; filename="resources.sqlite3"`）です。

```bash
# SQLite をダウンロード
curl -s -H "Authorization: Bearer mytoken" \
  http://127.0.0.1:8888/api/db -o captured.sqlite3

# DB Browser for SQLite 等で開いてパケット内容を確認
```

---

## エラーレスポンス

|        HTTP ステータス         |               意味               |
|---------------------------|--------------------------------|
| 400 Bad Request           | リクエストパラメータが不正                  |
| 401 Unauthorized          | `--api-key` 指定時に認証ヘッダーが不正または欠落 |
| 404 Not Found             | 指定 ID のリソースが存在しない              |
| 500 Internal Server Error | サーバー内部エラー                      |

```json
{"error": "エラーメッセージ"}
```

