#!/usr/bin/env node

/**
 * MCP HTTP Bridge
 * Bridges HTTP MCP requests to PacketProxy's HTTP endpoint
 */

const http = require('http');
const { spawn } = require('child_process');

// Configuration
const PACKETPROXY_HTTP_URL = 'http://localhost:8765/mcp';

// MCP Server implementation
class MCPHttpBridge {
    constructor() {
        this.initialized = false;
        this.serverInfo = {
            name: "PacketProxy MCP Bridge",
            version: "1.0.0"
        };
    }

    async handleRequest(request) {
        console.error(`[DEBUG] Processing request: ${request.method}`);

        switch (request.method) {
            case 'initialize':
                return this.handleInitialize(request);
            case 'notifications/initialized':
                return this.handleNotificationInitialized(request);
            case 'tools/list':
                return this.handleToolsList(request);
            case 'tools/call':
                return this.handleToolsCall(request);
            case 'resources/list':
                return this.handleResourcesList(request);
            case 'prompts/list':
                return this.handlePromptsList(request);
            default:
                return this.createErrorResponse(request.id, -32601, `Method not found: ${request.method}`);
        }
    }

    async handleInitialize(request) {
        this.initialized = true;
        console.error('[DEBUG] Initialize request - forwarding to PacketProxy');
        
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward initialize request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async handleNotificationInitialized(request) {
        console.error('[DEBUG] Notification initialized received (no response needed)');
        // Notifications don't require a response, return null
        return null;
    }

    async handleResourcesList(request) {
        console.error('[DEBUG] Resources list request - forwarding to PacketProxy');
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async handlePromptsList(request) {
        console.error('[DEBUG] Prompts list request - forwarding to PacketProxy');
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async handleToolsList(request) {
        if (!this.initialized) {
            return this.createErrorResponse(request.id, -32002, "Server not initialized");
        }

        console.error('[DEBUG] Tools list request - forwarding to PacketProxy');
        
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async handleToolsCall(request) {
        if (!this.initialized) {
            return this.createErrorResponse(request.id, -32002, "Server not initialized");
        }

        console.error(`[DEBUG] Tools call request: ${request.params?.name}`);
        
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async forwardToPacketProxy(request) {
        return new Promise((resolve, reject) => {
            const postData = JSON.stringify(request);
            
            console.error(`[DEBUG] Forwarding to PacketProxy: ${postData}`);
            
            const options = {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(postData)
                }
            };

            const req = http.request(PACKETPROXY_HTTP_URL, options, (res) => {
                let data = '';
                
                res.on('data', (chunk) => {
                    data += chunk;
                });
                
                res.on('end', () => {
                    try {
                        const response = JSON.parse(data);
                        console.error(`[DEBUG] PacketProxy response: ${data}`);
                        resolve(response);
                    } catch (error) {
                        reject(new Error(`Failed to parse PacketProxy response: ${error.message}`));
                    }
                });
            });

            req.on('error', (error) => {
                reject(new Error(`HTTP request failed: ${error.message}`));
            });

            req.write(postData);
            req.end();
        });
    }

    createErrorResponse(id, code, message) {
        return {
            jsonrpc: "2.0",
            id: id,
            error: {
                code: code,
                message: message
            }
        };
    }
}

// Main execution
async function main() {
    console.error('[DEBUG] PacketProxy MCP HTTP Bridge starting...');
    
    const bridge = new MCPHttpBridge();
    
    process.stdin.setEncoding('utf8');
    
    let buffer = '';
    
    process.stdin.on('data', async (chunk) => {
        buffer += chunk;
        
        // Process complete lines
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // Keep incomplete line in buffer
        
        for (const line of lines) {
            const trimmedLine = line.trim();
            if (!trimmedLine) continue;
            
            try {
                console.error(`[DEBUG] Received: ${trimmedLine}`);
                const request = JSON.parse(trimmedLine);
                const response = await bridge.handleRequest(request);
                
                // Only send response if it's not null (notifications don't need responses)
                if (response !== null) {
                    const responseStr = JSON.stringify(response);
                    console.log(responseStr);
                    console.error(`[DEBUG] Sent: ${responseStr}`);
                } else {
                    console.error(`[DEBUG] No response needed (notification)`);
                }
            } catch (error) {
                console.error(`[ERROR] Failed to process request: ${error.message}`);
                // Try to extract ID from the malformed request
                let requestId = null;
                try {
                    const partialRequest = JSON.parse(trimmedLine);
                    requestId = partialRequest.id || null;
                } catch (e) {
                    // If we can't parse at all, use null ID
                }
                
                const errorResponse = {
                    jsonrpc: "2.0",
                    id: requestId,
                    error: {
                        code: -32700,
                        message: "Parse error"
                    }
                };
                console.log(JSON.stringify(errorResponse));
            }
        }
    });
    
    process.stdin.on('end', () => {
        console.error('[DEBUG] Bridge shutting down');
        process.exit(0);
    });
    
    // Handle process termination
    process.on('SIGINT', () => {
        console.error('[DEBUG] Received SIGINT, shutting down');
        process.exit(0);
    });
}

if (require.main === module) {
    main().catch((error) => {
        console.error(`[FATAL] ${error.message}`);
        process.exit(1);
    });
}

module.exports = MCPHttpBridge;