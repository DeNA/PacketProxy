# PacketProxy CLI 版 実装計画 兼 引き継ぎドキュメント

> **目的（このファイル）**: 他のエージェント／担当者が作業を引き継げるよう、計画と進捗を一元管理する。
> 作業者は着手・完了のたびに下記「進捗トラッカー」を更新すること。
> 対象ブランチ: `feat/cli-ver`（起票時点で master と差分なし）。

## 進捗トラッカー（Status）

| # |              作業項目              |  状態   |                              担当/メモ                              |
|---|--------------------------------|-------|-----------------------------------------------------------------|
| 0 | ロジックのリグレッションテスト追加・グリーン確認       | ☑ 完了  | EncoderCharacterizationTest, EncoderManagerCharacterizationTest |
| 1 | build.gradle に picocli 追加      | ☑ 完了  | picocli 4.7.6                                                   |
| 2 | `PacketProxy.main` のサブコマンド分岐   | ☑ 完了  | CLI_SUBCOMMANDS で先頭 arg 判定                                      |
| 3 | picocli コマンド木（`cli.app`）       | ☑ 完了  | CliRoot, *Command 各クラス                                          |
| 4 | client 変換コア `Codec`            | ☑ 完了  | Codec.kt (stdin→encoder→stdout)                                 |
| 5 | server 前景ブロックモード               | ☑ 完了  | ServerCommand.kt + stopAll()                                    |
| 6 | `Logging.kt` に CLI console モード | ☑ 完了  | LogMode enum + init(LogMode) オーバーロード                            |
| 7 | `packetproxy-cli` 等スクリプト更新     | ☑ 完了  | 新サブコマンドを直接転送                                                    |
| 8 | CLI テスト                        | ☑ 完了  | CodecTest.kt (14 テスト)                                           |
| 9 | E2E 検証（下記「検証」）                 | ☐ 未着手 | `./gradlew installDist` 後に手動検証推奨                                |

状態凡例: ☐ 未着手 / ◐ 進行中 / ☑ 完了 / ⚠ 要相談・ブロック

### 決定事項（ユーザー確認済み）

- GUI は残し共存 / client mode は one-shot パイプ / 引数は picocli サブコマンド / server mode は前景ブロック。
- 実装前にロジックのリグレッションテストを書く（本人要望）。

### 引き継ぎメモ（次の作業者へ）

- 設計は既存 CLI 基盤（`--gulp` REPL, `AppInitializer`, `ConfigIO`, `EncoderManager`, `Encoder`）を
  **再利用**する前提。エンコードモジュールは無改変＝互換維持。
- 既存 gulp REPL（`GulpTerminal` / `packetproxy.cli` の `*ModeHandler`）は今回は温存（未実装スタブのまま）。
- 重要な落とし穴は本文「注意点」節を必読（picocli 未導入 / gulp ログはファイル出力 / `Logging` が
  `GUILog` を eager 初期化 / stdout をパイプ出力で汚さない）。

## Context（背景・目的）

PacketProxy を GUI なしでも使えるようにし、特に **CI 等のヘッドレス環境** と
**パイプによるデータ変換** で利用できるようにする。

要件:
1. 現状ロジックは温存し、GUI を CUI に置き換える。**エンコードモジュールは互換のまま動かす**。
2. `server mode`: PacketProxy サーバーを CLI で起動（GUI 以外は現状互換）。CI でも利用。
3. `client mode`: 標準入力を受け取り、オプション（encode/decode・text/binary・エンコード方式）に
従って指定エンコードモジュールで変換し、標準出力へ出す（パイプフィルタ）。

### 確認済みの方針（ユーザー決定）

- **GUI は残し共存**（`packetproxy.gui` / Swing 依存は削除しない。CI では CLI パスのみ通る）
- client mode は **ワンショットのパイプ**（1 回実行して終了）
- 引数は **picocli のサブコマンド**（`packetproxy <subcommand> --flags`、`--help`/検証付き）
- server mode は **専用の前景ブロックモード**（SIGINT/SIGTERM まで前景でブロック、TTY/REPL 不要）

## 現状（既に master 上に存在する CLI 基盤 — 再利用する）

- `PacketProxy.main`（`src/main/java/core/packetproxy/PacketProxy.java`）が `--gulp` /
  `--settings-json` を解釈し、GUI と対話型 REPL(`GulpTerminal`) に振り分ける。
- `AppInitializer`（`src/main/kotlin/core/packetproxy/AppInitializer.kt`）が GUI/CLI 共通の
  初期化（`initCore` / `initGulp` / `initComponents`）を提供。設定 JSON 読込もここ（`loadSettingsFromJson`）。
- 設定読込: `ConfigIO.setOptions(json)`（`src/main/java/core/packetproxy/common/ConfigIO.java`）が
  servers/listenports/modifications/sslPassThrough を DB に投入 → `ListenPortManager` が
  有効プロキシを **自動起動**。＝ server mode の中核は既に動く。
- エンコーダ基盤（**GUI 非依存・そのまま再利用＝互換維持**）:
  - `EncoderManager.getInstance().createInstance(name, alpn)` / `getEncoderNameList()`
  - `Encoder` の `encode/decodeClientRequest`・`encode/decodeServerResponse`（いずれも `byte[]→byte[]`）
  - `EncodeSample` で契約確認済み（4 メソッドは単一バッファ変換、Sample は恒等）。
- 既存の対話型 REPL(`GulpTerminal` と `packetproxy.cli` の `EncodeModeHandler`/`DecodeModeHandler`)
  は **スタブ**（`status` を出すだけ）。今回のワンショット要件では触らず温存する。
- `packetproxy-cli` ラッパー（`gradlew installDist` → 生成バイナリ実行、引数を `main()` へ転送）。

### 注意点（実装で必ず踏む）

- **picocli は未導入** → `build.gradle` に依存追加が必要（JLine は導入済み）。
- gulp モードのログは **ファイル**(`logs/gulp.log`) へ出る設計（`Logging.init(isGulp)`）。
  → client mode は **stdout を汚さない**（ログをファイル/抑制側に回す）。
  → server mode は CI でログを見せたいので **console(stderr) 出力経路** が要る。
- `Logging` は `GUILog.getInstance()` を eager 初期化するが、現 gulp モードで実績ありヘッドレス安全。

## 実装方針（概要）

`PacketProxy.main` を薄いルーターにし、先頭トークンが既知サブコマンドなら **picocli** へ委譲、
それ以外は現状動作（引数なし=GUI、`--gulp`/`--settings-json`=既存 REPL）を温存して後方互換を保つ。

```
packetproxy server  --config <file> [--no-log]      # 前景ブロック型サーバー
packetproxy encode  --encoder <name> [--response] [--text|--binary] [--in f][--out f]
packetproxy decode  --encoder <name> [--response] [--text|--binary] [--in f][--out f]
packetproxy encoders                                 # 利用可能エンコーダ一覧（getEncoderNameList）
（引数なし → GUI 起動 / 既存 --gulp も従来どおり動作）
```

## 作業項目

> 進め方: **まず項目 0（リグレッションテスト）を追加してグリーンを確認** → その後 1〜7 を実装。
> CLI 化はロジックを「再利用」する設計なので、デグレ検知の要は「再利用する既存ロジック」と
> 「自分が変更するファイル(`Logging.kt`/エントリポイント)」の挙動固定。

### 0. ロジックのリグレッションテスト（実装前に追加）

既存のエンコードモジュール直接テストは `EncodeHTTPWebSocketOpCodeTest` 程度で薄い。
CLI が依存・私が変更する範囲の挙動を**実装前に固定**し、デグレを検知できるようにする。
- **エンコーダ変換の特性化テスト**（新規 `src/test/java/packetproxy/encode/` か kotlin）:
代表エンコーダ（`Sample`・`SampleUpperCase`・`HTTP` など）について
`decode/encodeClientRequest` と `decode/encodeServerResponse` の入出力を固定。
→ CLI(`Codec`) が同一結果を返すことの基準になる。
- **`EncoderManager` 検出テスト**: `getEncoderNameList()` に既知エンコーダが含まれる／
`createInstance(name,null)` が非 null・未知名で null を返す（ヘッドレスで動く）こと。
- **`ConfigIO.setOptions/getOptions` ラウンドトリップ**: 設定 JSON 投入で
`ListenPorts`/`Servers` が復元される（server mode の前提）こと。
- **`Logging.init` 既存挙動の固定**: GUI=Console / gulp=File の現挙動を確認してから
CLI console モード追加（項目 6）で壊さないことを保証。
- 既存テストがグリーンであることも併せて確認（`./gradlew test`）。

### 1. build.gradle: picocli 依存追加

- `implementation 'info.picocli:picocli:4.7.6'` を `dependencies` に追加（fat-jar 構成に含まれる）。
- `mainClass`（`build.gradle:184`）は `packetproxy.PacketProxy` のまま（main 内でルーティング）。

### 2. エントリポイントのルーティング（`PacketProxy.java`）

- `main` 冒頭に「先頭 arg が `server`/`encode`/`decode`/`encoders` のいずれか」を判定する分岐を追加。
  該当時は新設 picocli ルート `CliRoot` に委譲し、終了コードで `System.exit`。
- 該当しなければ既存ロジック（`--gulp`／GUI）をそのまま実行（後方互換）。

### 3. picocli コマンド木（新規 `packetproxy.cli.app` パッケージ）

> 既存 `packetproxy.cli`（REPL ハンドラ）と区別するためサブパッケージに置く。Kotlin で実装。
> - `CliRoot`（`@Command(subcommands=...)`）: 共通の初期化呼び出しを担う。
> - 各サブコマンド実行前に `AppInitializer.setArgs(isGulp=true, settingsPath)` 相当 + `initCore()` を
> **CLI 用ログモード**で実行（stdout を汚さない）。
> - `EncodeCommand` / `DecodeCommand`: client mode（下記 4 の `Codec` を呼ぶ）。
> - `ServerCommand`: server mode（下記 5）。
> - `EncodersCommand`: `EncoderManager.getEncoderNameList()` を出力。

### 4. クライアントモード変換コア（新規 `Codec`）

- 入力取得: `--in`（無指定なら `System.in`）から **全バイト読込**。
- エンコーダ生成: `EncoderManager.getInstance().createInstance(name, alpn=null)`。
  `null`（未知名）なら利用可能一覧を stderr に出してエラー終了（exit≠0）。
- 変換方向:
  - `encode`+request → `encodeClientRequest`、`encode`+`--response` → `encodeServerResponse`
  - `decode`+request → `decodeClientRequest`、`decode`+`--response` → `decodeServerResponse`
  - 既定は request 方向。
- text/binary（既定 binary）:
  - `--binary`: stdin/stdout を **生バイト**でそのまま入出力。
  - `--text`: UTF-8 として扱い、出力末尾の改行有無を整える（CLI 既定の text 挙動）。
- 出力: `--out`（無指定なら `System.out`）へ変換後バイトを書く。**ログは混ぜない**。
- 例外時は stderr にメッセージ、exit≠0。

### 5. サーバーモード（`ServerCommand`）

- `--config` の JSON を読み、`ConfigIO().setOptions(json)`（既存）で投入 → `ListenPortManager`
  初期化（`AppInitializer.initComponents()` 経由）で有効プロキシ自動起動。
- ログは **console(stderr)** へ（CI で可視）。`--no-log` で抑制可。
- 前景ブロック: `CountDownLatch` で待機。`Runtime.getRuntime().addShutdownHook` で
  SIGINT/SIGTERM 受信時に listen を全 close（`ListenPortManager` の停止経路を利用）し latch を解放。

### 6. ログ整理（`Logging.kt`）

- `Logging.init` に **CLI コンソール(stderr)モード** を追加（現状: GUI=Console / gulp=File の二択）。
  - client(encode/decode): ファイル or 抑制（stdout を汚さない）。
  - server: console(stderr)。
- 既存 `init(isGulp: Boolean)` の呼び出し互換は維持（オーバーロード追加 or enum 引数）。

### 7. ラッパー/スクリプト更新

- `packetproxy-cli`（リポジトリ直下）を新サブコマンド呼び出しに更新、または別途
  `packetproxy` ラッパーを用意（`installDist` 後の bin を `exec "$BIN" "$@"`）。
- 既存 `scripts/*.pp` と `--gulp` 経路は温存。

### 8. CLI テスト（項目 0 の特性化テストの上に積む）

- `src/test/kotlin/packetproxy/cli/` に:
  - `CodecTest`: Sample（恒等）/ 変換系エンコーダで encode/decode・request/response・text/binary を検証。
    項目 0 の特性化テストと同じ期待値を使い、CLI 経路が既存ロジックと一致することを保証。
  - picocli パース/未知エンコーダのエラー終了・`encoders` 出力の検証。
- 既存 gulp テスト（`src/test/kotlin/packetproxy/gulp/`）・項目 0 のテストは壊さない。

## 変更・新規ファイル

- 変更: `build.gradle`（picocli）、`src/main/java/core/packetproxy/PacketProxy.java`（ルーティング）、
  `src/main/kotlin/core/packetproxy/util/Logging.kt`（CLI コンソールモード）、`packetproxy-cli`。
- 新規: `src/main/kotlin/core/packetproxy/cli/app/CliRoot.kt`、`ServerCommand.kt`、
  `EncodeCommand.kt`、`DecodeCommand.kt`、`EncodersCommand.kt`、`Codec.kt`、
  および `src/test/kotlin/packetproxy/cli/CodecTest.kt` ほか。

## 検証（エンドツーエンド）

1. ビルド: `./gradlew installDist`（または `spotlessApply` 後）。バイナリ: `build/install/PacketProxy/bin/PacketProxy`。
2. client mode（恒等）: `printf 'hello' | <bin> encode --encoder Sample` → `hello`。
3. client mode（変換）: `EncodeSampleUpperCase` 等で decode/encode の方向・大文字変換を確認。
   `<bin> encoders` で一覧が出ること。未知名は exit≠0 + stderr メッセージ。
4. server mode: 最小 `config.json`（HTTP_PROXY listen + server）で `<bin> server --config config.json` →
   別端末から `curl -x http://127.0.0.1:<port> http://example.com` が通る。
   Ctrl+C（SIGINT）/ `kill`（SIGTERM）で listen が閉じ正常終了（CI 想定）。stdout は変換用途を汚さない。
5. ユニットテスト: `./gradlew test`。
6. 後方互換: 引数なしで GUI 起動、`--gulp` REPL が従来どおり動作することを確認。

## 既定値・前提（実装時に確定、必要なら調整可）

- client mode の変換方向 既定は **request**（`--response` で server 側）。
- text/binary 既定は **binary**（生バイト通過）。`--text` は UTF-8 + 改行整形。
- 単一バッファをそのまま 1 回変換（パイプフィルタ用途。delimiter フレーミングはしない）。
- 既存 gulp REPL の encode/decode スタブは今回は未実装のまま温存（要件は one-shot のため）。

