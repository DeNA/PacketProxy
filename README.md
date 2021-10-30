![PacketProxy](./assets/images/packetproxy.png)

[![CircleCI](https://circleci.com/gh/DeNA/PacketProxy/tree/master.svg?style=svg)](https://circleci.com/gh/DeNA/PacketProxy/tree/master)

[日本語のREADMEはここ](https://github.com/DeNA/PacketProxy/blob/master/README.ja.md)

# About PacketProxy

PacketProxy is an open-source proxy tool that can intercept and inspect any protocol over TCP/UDP, not limited to just HTTP/1.x, HTTP2, or HTTPS.

PacketProxy is designed for testing web applications for internal use, which helps app development and finding vulnerabilities on your applications.
 
# Screenshot

![ScreenShot](./assets/images/screenshot.gif)
 
# Features
 
- **Full-featured local proxy tool**
  - Save all captured packets (i.e., requests and responses) and show them in the history tab
  - Filter/Search packets in the history tab (e.g. `requests==/api/v1/users` to show only requests whose path contains `/api/v1/users` )
  - Modify the contents of intercepted packets before forwarding them to the destination
  - Resend (or replay) captured packets with or without manually modifying the contents
  - Show differences between any two received packets
- **Support for protocols over TCP/UDP, not limited to HTTP/HTTPS**
  - Built-in encoders/decoders enable users to inspect/intercept HTTP1, HTTP2, HTTPS, WebSocket, FireBase, Firestore, MQTT, gRPC, Protocol Buffers, MessagePack, and CBOR messages
  - Easy to develop an extension to decode/encode protocols not listed above
- **Features for application penetration tests**
  - Send multiple packets simultaneously to test race conditions or any inconsistent state due to improper synchronization/locking.
  - Even possible to simultaneously send packets with different messages. ( `Bulk Sender` )
  - Replace a server certificate with a self-signed one for testing if a client app properly validate certificates
  - Embedded an easy to configure DNS server to route requests from a client to PacketProxy, which is one of the easiest ways to proxy packets transparently
  - Save/Load settings and history data of a project as an SQLite3 file
  - Support Windows, macOS, and Linux

# Install

The recommended way to install PacketProxy is to download an installer for your platform from the PacketProxy's [release](https://github.com/DeNA/PacketProxy/releases) page and execute it.

You may also use Homebrew-Cask to install PacketProxy if your platform is macOS. In that case, installation is done by just typing `$ brew cask install packetproxy`

# Using PacketProxy

Take a look at this page to get started: [For Users](https://github.com/DeNA/PacketProxy/wiki/Using-PacketProxy)
 
# Extending PacketProxy

If you want to develop an extension to support additional protocols or improve core functionalities/UIs, please have a look at this page: [For Developers](https://github.com/DeNA/PacketProxy/wiki/Developing-PacketProxy)

 
# License

This program is distributed under Apache License 2.0

