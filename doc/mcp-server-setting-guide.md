# PacketProxy MCP サーバー設定ガイド

## 概要

このガイドでは、PacketProxy MCP サーバーをClaude Desktopから使用するための設定方法を説明します。

**🔄 HTTP ベースアプローチ**: このガイドではHTTPベースのMCP接続を使用し、PacketProxy GUI内のMCPサーバーを直接利用します。これによりPythonの依存関係問題を回避できます。

## 前提条件

- PacketProxy がビルド済み (`./gradlew build` 実行済み)
- Claude Desktop がインストール済み
- Node.js がインストール済み (npxコマンド用)
- Java 17以降がインストール済み

## 設定手順

### 1. PacketProxy GUIの起動とMCPサーバーの有効化

まず PacketProxy GUI を起動し、MCP サーバーを有効にします：

```bash
# PacketProxyを起動
java -jar build/libs/PacketProxy.jar
```

GUI起動後：
1. **Options** → **Extensions** を選択
2. **MCP Server** を選択  
3. **Enable** にチェックを入れる
4. **Start Server** ボタンをクリック

ログに以下が表示されることを確認：

```
MCP Server started
HTTP endpoint available at http://localhost:8765/mcp
```

### 2. アクセストークンの取得

PacketProxy GUIでアクセストークンを有効化し、トークンを取得します：

1. **Options** → **Setting** を選択
2. **Import/Export configs** セクションで **Enable** にチェックを入れる
3. 表示されたアクセストークンをコピーしておく

### 3. Claude Desktop設定ファイルの編集

Claude Desktopの設定ファイルを編集します：

```bash
# 設定ファイルの場所
open ~/Library/Application\ Support/Claude/claude_desktop_config.json
```

以下の内容を追加してください（`your_access_token_here`の部分を手順2で取得したアクセストークンに置き換えてください）：

```json
{
    "mcpServers": {
        "packetproxy": {
            "command": "node",
            "args": [
                "/Users/kakira/PacketProxy/scripts/mcp-http-bridge.js"
            ],
            "env": {
                "PACKETPROXY_ACCESS_TOKEN": "your_access_token_here"
            }
        }
    }
}
```

**既存の設定がある場合の例：**

```json
{
    "mcpServers": {
        "filesystem": {
            "command": "npx",
            "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/directory"]
        },
        "packetproxy": {
            "command": "node",
            "args": [
                "/Users/kakira/PacketProxy/scripts/mcp-http-bridge.js"
            ],
            "env": {
                "PACKETPROXY_ACCESS_TOKEN": "your_access_token_here"
            }
        }
    }
}
```

### 4. Claude Desktopの再起動

設定を反映するためにClaude Desktopを完全に終了して再起動してください：

1. Claude Desktop → Quit Claude
2. Claude Desktopを再起動

## 使用方法

### PacketProxyとClaude Desktopの連携テスト

**前提条件：**
1. PacketProxy GUIが起動している
2. MCP Serverが有効化され、起動している
3. Claude Desktopが再起動済み

Claude Desktopで新しい会話を開始し、以下を試してください：

```
PacketProxyのツールを使って、利用可能な機能を教えてください
```

**期待される応答：**
- `get_history`: パケット履歴の取得
- `get_configs`: 設定情報の取得  
- `get_packet_detail`: パケット詳細情報の取得

### 実際のPacketProxy操作

PacketProxyでトラフィックをキャプチャした後、Claude Desktopから操作できます：

```
PacketProxyからパケット履歴を5件取得してください
```

```
PacketProxyの設定情報を確認してください
```

```
パケットID 1の詳細を取得してください
```

## トラブルシューティング

### 設定確認

**1. 設定ファイルの確認**

```bash
cat "/Users/kakira/Library/Application Support/Claude/claude_desktop_config.json"
# packetproxyの設定にnpxと@modelcontextprotocol/server-fetchが含まれていることを確認
```

**2. PacketProxyのHTTPエンドポイント確認**

```bash
# PacketProxy GUIでMCPサーバーが起動している場合
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

**3. Node.jsとnpxの確認**

```bash
node --version
npx --version
# 両方のコマンドがバージョンを返すことを確認
```

### よくある問題と対処法

**1. Claude Desktopでツールが認識されない**
- Claude Desktopを完全に再起動
- 設定ファイルのJSON構文を確認
- PacketProxy GUIでMCPサーバーが起動していることを確認

**2. HTTP接続エラー**

```bash
# PacketProxyのHTTPエンドポイントが応答するか確認
curl -v http://localhost:8765/mcp

# ポート8765が使用中か確認
lsof -i :8765
```

**3. Node.jsブリッジのテスト**

```bash
# ブリッジが正常に動作するかテスト
echo '{"jsonrpc": "2.0", "method": "initialize", "params": {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "test", "version": "1.0"}}, "id": 0}' | node /Users/kakira/PacketProxy/scripts/mcp-http-bridge.js
```

**4. PacketProxy JARファイルが見つからない**

```bash
# ビルドを実行
cd /Users/kakira/PacketProxy
./gradlew build

# JARファイルの存在確認
ls -la build/libs/PacketProxy.jar
```

## 利用可能なツール

### `get_history`

PacketProxyのパケット履歴を取得します。

**パラメータ：**
- `limit` (integer, optional): 取得するパケット数 (デフォルト: 100)
- `offset` (integer, optional): オフセット (デフォルト: 0)

**使用例：**

```
最新のパケット履歴を5件取得してください
```

### `get_configs`

PacketProxyの設定情報を取得します。

**パラメータ：**
- `categories` (array, optional): 取得する設定カテゴリ

**使用例：**

```
PacketProxyの現在の設定を確認してください
```

### `get_packet_detail`

特定のパケットの詳細情報を取得します。

**パラメータ：**
- `packet_id` (integer, required): パケットID
- `include_body` (boolean, optional): ボディを含めるか (デフォルト: false)
- `include_pair` (boolean, optional): ペアパケットを含めるか (デフォルト: false)

**使用例：**

```
パケットID 123の詳細情報を取得してください
```

## サポート

問題が発生した場合は、以下を確認してください：

1. PacketProxyのビルドが成功していること
2. Claude Desktopの設定ファイルが正しい形式であること
3. スクリプトファイルに実行権限があること
4. Python3が正常に動作すること

詳細な技術仕様については `doc/mcp-server-spec.md` を参照してください。
