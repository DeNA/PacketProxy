#!/bin/bash

# PacketProxy MCP Server launcher script
# This script starts PacketProxy in headless mode with MCP server enabled

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKETPROXY_DIR="$(dirname "$SCRIPT_DIR")"
JAR_FILE="$PACKETPROXY_DIR/build/libs/PacketProxy.jar"

# Check if PacketProxy jar exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: PacketProxy.jar not found at $JAR_FILE" >&2
    echo "Please run 'gradlew build' first" >&2
    exit 1
fi

# Start PacketProxy with MCP server in headless mode
# We need to start PacketProxy GUI and enable MCP server programmatically
java -jar "$JAR_FILE" --mcp-server-mode
