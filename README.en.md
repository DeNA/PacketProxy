![PacketProxy](./assets/images/packetproxy.png)

[![CircleCI](https://circleci.com/gh/DeNA/PacketProxy/tree/master.svg?style=svg)](https://circleci.com/gh/DeNA/PacketProxy/tree/master)

# PacketProxy

PacketProxy is an open source proxy tool that allows TCP/IP Traffic to be read. It is not limited to just HTTP/HTTPS.

It can be used for development assistance or for risk assessments.
 
# Screenshot

![ScreenShot](./assets/images/screenshot.gif)
 
# Features
 
- **ローカルプロキシツールとしての主要な機能が備わっています**
  - 到着パケットの履歴の表示・フィルタ
  - パケットデータの表示・検索・変換・Diff
  - インターセプト
  - 加工して再送
- **HTTP/HTTPS通信だけでなく、より低レイヤーのTCP/UDP通信にも対応しています**
  - メジャーなプロトコル（HTTP、HTTPS、WebSocket、FireBase、MQTT、Protocol Buffers、MessagePack、CBOR）はビルトイン済み（増やす予定あり）
  - 新しいプロトコル（例：特定ゲームの独自通信プロトコル等）への拡張が簡単
- **脆弱性診断で利用できる便利な機能を用意しています**
  - パケットを連続して同時に送信する機能（同時複数送信）
  - 内容の異なるパケットを同時に送信する機能（バルク送信）
  - 自己署名証明書によるHTTPS通信をMITMできてしまうかチェックする機能
  - DNSサーバが内蔵されており、DNS書き換えによるパケットのフォワード機能

# Setup
  
[Download](https://github.com/DeNA/PacketProxy/releases) the version corresponding to your OS and run the installer. 

# Usage

Please see this page: [For Users](https://github.com/DeNA/PacketProxy/wiki/%E4%BD%BF%E3%81%A3%E3%81%A6%E3%81%BF%E3%82%8B)
 
# Development

If you want to develop support for additional protocols or improve on the base PacketProxy application, please refer to this page:
[For Developers](https://github.com/DeNA/PacketProxy/wiki/%E9%96%8B%E7%99%BA%E3%81%99%E3%82%8B)

 
# License

Apache License 2.0

