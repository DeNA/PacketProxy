![PacketProxy](./assets/images/packetproxy.png)

[![CircleCI](https://circleci.com/gh/DeNA/PacketProxy/tree/master.svg?style=svg)](https://circleci.com/gh/DeNA/PacketProxy/tree/master)

# PacketProxy

PacketProxy is an open source proxy tool that allows TCP/UDP Traffic to be read. It is not limited to just HTTP/HTTPS.

It can be used for development assistance or for risk assessments.
 
# Screenshot

![ScreenShot](./assets/images/screenshot.gif)
 
# Features
 
- **Major features for local proxy tool**
  - History and filtering of packets' data
  - Show, search, modify, and diff packets' data
  - Interception
  - Modify and resend
- **Handling not only HTTP/HTTPS, but also TCP/UDP connection**
  - Major protocols such as HTTP, HTTPS, WebSOcket, FireBase, MQTT, Protocol Buffers, MessagePack and CBOR are built-in
  - You can Make a plugin for new protocols easily
- **Features for a manual application penetration test**
  - Send concurrent multiple packets for a DB transaction test
  - Change server certificate to self signed certificate for a test of client side validation
  - Built-in DNS server for packet forwarding with modifying DNS responses automatically
  - Save and load current project data as SQLite
  - Support Windows and macOS

# Setup
  
[Download](https://github.com/DeNA/PacketProxy/releases) the version corresponding to your OS and run the installer. 

# Usage

Please see this page: [For Users](https://github.com/DeNA/PacketProxy/wiki/%E4%BD%BF%E3%81%A3%E3%81%A6%E3%81%BF%E3%82%8B)
 
# Development

If you want to develop support for additional protocols or improve on the base PacketProxy application, please refer to this page:
[For Developers](https://github.com/DeNA/PacketProxy/wiki/%E9%96%8B%E7%99%BA%E3%81%99%E3%82%8B)

 
# License

Apache License 2.0

