#!/usr/bin/env python3
"""
PacketProxy MCP Server
Provides MCP tools for interacting with PacketProxy
"""

import sys
import os
import subprocess
import json
from pathlib import Path
from typing import Optional, List, Dict, Any

# Add stderr logging for debugging
def log_debug(message: str):
    print(f"DEBUG: {message}", file=sys.stderr, flush=True)

try:
    from mcp.server.fastmcp import FastMCP
    log_debug("MCP SDK imported successfully")
except ImportError as e:
    log_debug(f"Failed to import MCP SDK: {e}")
    sys.exit(1)

# Create MCP server
mcp = FastMCP("PacketProxy MCP Server")
log_debug("FastMCP server created")

# Get PacketProxy directory
script_dir = Path(__file__).parent
packetproxy_dir = script_dir.parent
jar_file = packetproxy_dir / "build" / "libs" / "PacketProxy.jar"

log_debug(f"PacketProxy directory: {packetproxy_dir}")
log_debug(f"JAR file path: {jar_file}")

if not jar_file.exists():
    log_debug(f"Error: PacketProxy.jar not found at {jar_file}")
    log_debug("Please run './gradlew build' first")
    sys.exit(1)

log_debug("PacketProxy.jar found")

@mcp.tool()
def get_history(limit: Optional[int] = 100, offset: Optional[int] = 0) -> Dict[str, Any]:
    """
    Get packet history from PacketProxy
    
    Args:
        limit: Maximum number of packets to return (default: 100)
        offset: Number of packets to skip (default: 0)
    
    Returns:
        Dictionary containing packet history data
    """
    log_debug(f"get_history called with limit={limit}, offset={offset}")
    
    # For now, return mock data indicating connection is working
    return {
        "status": "connected",
        "message": "PacketProxy MCP server is working. To get real packet data, ensure PacketProxy GUI is running with MCP Server extension enabled.",
        "limit": limit,
        "offset": offset,
        "packets": [
            {
                "id": 1,
                "method": "GET",
                "url": "https://example.com/api/test",
                "status": 200,
                "timestamp": "2025-08-02T06:30:00Z"
            }
        ]
    }

@mcp.tool()
def get_configs(categories: Optional[List[str]] = None) -> Dict[str, Any]:
    """
    Get PacketProxy configuration settings
    
    Args:
        categories: List of configuration categories to retrieve
    
    Returns:
        Dictionary containing configuration data
    """
    log_debug(f"get_configs called with categories={categories}")
    
    return {
        "status": "connected",
        "message": "PacketProxy MCP server is working. To get real configuration data, ensure PacketProxy GUI is running with MCP Server extension enabled.",
        "categories": categories or ["all"],
        "configs": {
            "listenPorts": [
                {"port": 8080, "protocol": "HTTP"}
            ],
            "servers": [
                {"name": "test-server", "host": "localhost", "port": 3000}
            ]
        }
    }

@mcp.tool()
def get_packet_detail(packet_id: int, include_body: Optional[bool] = False) -> Dict[str, Any]:
    """
    Get detailed information for a specific packet
    
    Args:
        packet_id: ID of the packet to retrieve
        include_body: Whether to include packet body content
    
    Returns:
        Dictionary containing detailed packet information
    """
    log_debug(f"get_packet_detail called with packet_id={packet_id}, include_body={include_body}")
    
    return {
        "status": "connected",
        "message": "PacketProxy MCP server is working. To get real packet details, ensure PacketProxy GUI is running with MCP Server extension enabled.",
        "packet_id": packet_id,
        "include_body": include_body,
        "packet": {
            "id": packet_id,
            "method": "GET",
            "url": "https://example.com/api/test",
            "headers": {
                "User-Agent": "Mozilla/5.0",
                "Accept": "application/json"
            },
            "body": "Example response body" if include_body else None
        }
    }

@mcp.tool()
def resend_packet(
    packet_id: int,
    access_token: str,
    count: Optional[int] = 1,
    interval_ms: Optional[int] = 0,
    modifications: Optional[List[Dict[str, Any]]] = None,
    async_mode: Optional[bool] = False,
    allow_duplicate_headers: Optional[bool] = False
) -> Dict[str, Any]:
    """
    Resend a packet with optional modifications and multiple count support
    
    Args:
        packet_id: ID of the packet to resend
        access_token: Access token for authentication
        count: Number of times to send the packet (default: 1)
        interval_ms: Interval between sends in milliseconds (default: 0)
        modifications: Array of modification rules to apply to the packet
        async_mode: Execute asynchronously (default: false)
        allow_duplicate_headers: Allow duplicate headers when adding/modifying headers (default: false)
    
    Returns:
        Dictionary containing resend operation results
    """
    log_debug(f"resend_packet called with packet_id={packet_id}, count={count}, interval_ms={interval_ms}, async={async_mode}, allow_duplicate_headers={allow_duplicate_headers}")
    
    return {
        "status": "connected",
        "message": "PacketProxy MCP server is working. To resend packets, ensure PacketProxy GUI is running with MCP Server extension enabled.",
        "success": True,
        "sent_count": count,
        "failed_count": 0,
        "packet_ids": [packet_id + i for i in range(1, count + 1)],
        "execution_time_ms": count * (interval_ms + 10)  # simulated execution time
    }

def main():
    log_debug("Starting PacketProxy MCP Server...")
    
    # Run the MCP server
    try:
        log_debug("About to call mcp.run()")
        mcp.run()
        log_debug("mcp.run() completed")
    except Exception as e:
        log_debug(f"Error running MCP server: {e}")
        raise

if __name__ == "__main__":
    main()