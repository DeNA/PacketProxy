![PacketProxy](./assets/images/packetproxy.png)

# PacketProxyとは？

PacketProxyは、HTTP/HTTPS通信だけでなく、より低レイヤーのTCP/UDPを利用したバイナリ通信にも対応した、オープンソースのローカルプロキシツールです。
  
個人や社内の内部アプリケーションの開発補助や脆弱性診断での利用を目的にしています。
 
# スクリーンショット

![ScreenShot](./assets/images/screenshot.gif)
 
# 特徴

- **ローカルプロキシツールとしての主要な機能が備わっています**
  - 到着パケットの履歴の表示・フィルタ
  - パケットデータの表示・検索・変換・Diff
  - インターセプト
  - 加工して再送
- **HTTP/HTTPS通信だけでなく、より低レイヤーのTCP/UDP通信にも対応しています**
  - メジャーなプロトコル（HTTP、HTTPS、WebSocket、FireBase、MQTT、Protocol Buffers）はビルトイン済み（増やす予定あり）
  - 新しいプロトコル（例：特定ゲームの独自通信プロトコル等）への拡張が簡単
- **脆弱性診断で利用できる便利な機能を用意しています**
  - パケットを連続して同時に送信する機能（同時複数送信）
  - 内容の異なるパケットを同時に送信する機能（バルク送信）
  - 自己署名証明書によるHTTPS通信をMITMできてしまうかチェックする機能
  - DNSサーバが内臓されており、DNS書き換えによるパケットのフォワード機能

# インストール

### 前準備（Javaのインストール）

PacketProxyは、現在のところ *JDK 8* で動作します。
OpenJDK 8(例:[Amazon Corretto 8](https://docs.aws.amazon.com/ja_jp/corretto/latest/corretto-8-ug/downloads-list.html)) または [Oracle JDK 8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) をダウンロードしてインストールしてください。

JREでは動作しませんので、JDKを必ずインストールしてください。

  
### PacketProxyのインストール
  
[ダウンロードページ](https://github.com/DeNA/PacketProxy/releases)より、各OS用のイメージをダウンロードしてインストールしてください。

# 使ってみる

[利用者のページ](https://github.com/DeNA/PacketProxy/wiki/%E4%BD%BF%E3%81%A3%E3%81%A6%E3%81%BF%E3%82%8B)をみてください。
 
# 開発する

新しいプロトコル（例：特定ゲームの独自通信プロトコル等）への拡張を開発したい場合や、PacketProxyの本体を開発したい場合は
[開発者のページ](https://github.com/DeNA/PacketProxy/wiki/%E9%96%8B%E7%99%BA%E3%81%99%E3%82%8B)
をみてください。
 
# ライセンス

Apache License 2.0

