![PacketProxy](./assets/images/packetproxy.png)

[![CircleCI](https://circleci.com/gh/DeNA/PacketProxy/tree/master.svg?style=svg)](https://circleci.com/gh/DeNA/PacketProxy/tree/master)

[日本語のREADMEはここ](https://github.com/DeNA/PacketProxy/blob/master/README.ja.md)

# PacketProxy

PacketProxy is an open source proxy tool that allows TCP/UDP Traffic to be read. It is not limited to just HTTP.

It can be used for development assistance or for risk assessments.
 
# Screenshot

![ScreenShot](./assets/images/screenshot.gif)
 
# Features
 
- **Full featured local proxy tool**
  - Show all the received packets
  - Filter/Search packets by keywords
  - Intercept packets, and modify them before sending them to server
  - Resend packet to server
  - Show differences between any two received packets
- **Many protocols support**
  - Support HTTP1, HTTP2, HTTPS, WebSocket, FireBase, MQTT, gRPC, Protocol Buffers, MessagePack and CBOR as built-in protocols.
  - Develop new protocols easily
- **Features for application penetration test**
  - Send concurrently multiple packets for a DB transaction test
  - Replace server certificate with self signed certificate for a client side validation test
- **Other advantages**
  - Have a built-in DNS server for easy packet forwarding
  - Save/Load project data
  - Support Windows and macOS

# Setup
  
[Download](https://github.com/DeNA/PacketProxy/releases) the version corresponding to your OS and run the installer. 

# Usage

Please see this page: [For Users](https://github.com/DeNA/PacketProxy/wiki/Using-PacketProxy)
 
# Development

If you want to develop support for additional protocols or improve on the base PacketProxy application, please refer to this page:
[For Developers](https://github.com/DeNA/PacketProxy/wiki/Developing-PacketProxy)

 
# License

Apache License 2.0

