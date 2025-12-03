#!/usr/bin/env node

/**
 * MCP HTTP Bridge
 * Bridges HTTP MCP requests to PacketProxy's HTTP endpoint
 */

const http = require('http');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

// Configuration
const PACKETPROXY_HTTP_URL = 'http://localhost:8765/mcp';
const LOCK_FILE = path.join(os.tmpdir(), 'mcp-http-bridge.lock');
const DEBUG_MODE = process.env.MCP_DEBUG === 'true';

// Debug logging function
function debugLog(message) {
    if (DEBUG_MODE) {
        console.error(message);
    }
}

// Single instance enforcement
function ensureSingleInstance() {
    try {
        // Check if lock file exists and process is still running
        if (fs.existsSync(LOCK_FILE)) {
            const pidStr = fs.readFileSync(LOCK_FILE, 'utf8').trim();
            const pid = parseInt(pidStr, 10);
            
            if (!isNaN(pid)) {
                try {
                    // Check if process is still running
                    process.kill(pid, 0);
                    console.error(`[ERROR] Another instance is already running (PID: ${pid})`);
                    process.exit(1);
                } catch (err) {
                    // Process not running, remove stale lock file
                    fs.unlinkSync(LOCK_FILE);
                }
            } else {
                // Invalid PID in lock file, remove it
                fs.unlinkSync(LOCK_FILE);
            }
        }
        
        // Create lock file with current PID
        fs.writeFileSync(LOCK_FILE, process.pid.toString());
        
        // Clean up lock file on exit
        const cleanup = () => {
            try {
                if (fs.existsSync(LOCK_FILE)) {
                    fs.unlinkSync(LOCK_FILE);
                }
            } catch (err) {
                // Ignore cleanup errors
            }
        };
        
        process.on('exit', cleanup);
        process.on('SIGINT', cleanup);
        process.on('SIGTERM', cleanup);
        process.on('uncaughtException', (err) => {
            console.error('[FATAL] Uncaught exception:', err);
            cleanup();
            process.exit(1);
        });
        
    } catch (error) {
        console.error(`[ERROR] Failed to ensure single instance: ${error.message}`);
        process.exit(1);
    }
}

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
        debugLog(`[DEBUG] Processing request: ${request.method}`);

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
            case 'resources/templates/list':
                return this.handleResourcesTemplatesList(request);
            case 'prompts/list':
                return this.handlePromptsList(request);
            default:
                return this.createErrorResponse(request.id, -32601, `Method not found: ${request.method}`);
        }
    }

    async handleInitialize(request) {
        this.initialized = true;
        debugLog('[DEBUG] Initialize request - forwarding to PacketProxy');
        
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward initialize request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async handleNotificationInitialized(request) {
        debugLog('[DEBUG] Notification initialized received (no response needed)');
        // Notifications don't require a response, return null
        return null;
    }

    async handleResourcesList(request) {
        debugLog('[DEBUG] Resources list request - forwarding to PacketProxy');
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async handleResourcesTemplatesList(request) {
        debugLog('[DEBUG] Resources templates list request - forwarding to PacketProxy');
        try {
            const response = await this.forwardToPacketProxy(request);
            return response;
        } catch (error) {
            console.error(`[ERROR] Failed to forward request: ${error.message}`);
            return this.createErrorResponse(request.id, -32603, `Internal error: ${error.message}`);
        }
    }

    async handlePromptsList(request) {
        debugLog('[DEBUG] Prompts list request - forwarding to PacketProxy');
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

        debugLog('[DEBUG] Tools list request - forwarding to PacketProxy');
        
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

        debugLog(`[DEBUG] Tools call request: ${request.params?.name}`);
        
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
            // 環境変数からアクセストークンを取得
            const accessToken = process.env.PACKETPROXY_ACCESS_TOKEN;
            debugLog(`[DEBUG] Environment variable PACKETPROXY_ACCESS_TOKEN: ${accessToken ? '[SET]' : '[NOT SET]'}`);
            debugLog(`[DEBUG] Request method: ${request.method}`);
            debugLog(`[DEBUG] Request params: ${JSON.stringify(request.params)}`);
            
            // tools/callリクエストの場合、arguments内にアクセストークンを追加
            if (accessToken && request.method === 'tools/call' && request.params) {
                if (!request.params.arguments) {
                    request.params.arguments = {};
                }
                if (!request.params.arguments.access_token) {
                    request.params.arguments.access_token = accessToken;
                    debugLog(`[DEBUG] Added access token to tools/call arguments`);
                }
            }
            // その他のリクエストでparamsが存在する場合
            else if (accessToken && request.params && typeof request.params === 'object') {
                if (!request.params.access_token) {
                    request.params.access_token = accessToken;
                    debugLog(`[DEBUG] Added access token to request params`);
                }
            } else if (!accessToken) {
                debugLog(`[WARNING] PACKETPROXY_ACCESS_TOKEN environment variable not set`);
            }
            
            const postData = JSON.stringify(request);
            
            debugLog(`[DEBUG] Forwarding to PacketProxy: ${postData}`);
            debugLog(`[DEBUG] Target URL: ${PACKETPROXY_HTTP_URL}`);
            
            const options = {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(postData)
                },
                timeout: 60000  // 60 second timeout
            };

            const req = http.request(PACKETPROXY_HTTP_URL, options, (res) => {
                let data = '';
                
                res.on('data', (chunk) => {
                    data += chunk;
                });
                
                res.on('end', () => {
                    debugLog(`[DEBUG] Raw response from PacketProxy (status: ${res.statusCode}): ${data}`);
                    
                    if (res.statusCode !== 200) {
                        console.error(`[ERROR] HTTP error from PacketProxy: ${res.statusCode} ${res.statusMessage}`);
                        reject(new Error(`HTTP error: ${res.statusCode} ${res.statusMessage}`));
                        return;
                    }
                    
                    try {
                        const response = JSON.parse(data);
                        debugLog(`[DEBUG] PacketProxy response parsed successfully`);
                        resolve(response);
                    } catch (error) {
                        console.error(`[ERROR] Failed to parse PacketProxy response: ${error.message}`);
                        console.error(`[ERROR] Raw data: ${data}`);
                        reject(new Error(`Failed to parse PacketProxy response: ${error.message}`));
                    }
                });
            });

            req.on('error', (error) => {
                console.error(`[ERROR] HTTP request to PacketProxy failed: ${error.message}`);
                console.error(`[ERROR] Error code: ${error.code}`);
                reject(new Error(`HTTP request failed: ${error.message}`));
            });

            req.on('timeout', () => {
                console.error(`[ERROR] HTTP request to PacketProxy timed out`);
                req.destroy();
                reject(new Error('HTTP request timed out'));
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
    // Ensure only one instance can run
    ensureSingleInstance();
    
    debugLog('[DEBUG] PacketProxy MCP HTTP Bridge starting...');
    
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
                debugLog(`[DEBUG] Received: ${trimmedLine}`);
                const request = JSON.parse(trimmedLine);
                const response = await bridge.handleRequest(request);
                
                // Only send response if it's not null (notifications don't need responses)
                if (response !== null) {
                    const responseStr = JSON.stringify(response);
                    process.stdout.write(responseStr + '\n');
                    debugLog(`[DEBUG] Sent: ${responseStr.length} characters`);
                } else {
                    debugLog(`[DEBUG] No response needed (notification)`);
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
                process.stdout.write(JSON.stringify(errorResponse) + '\n');
            }
        }
    });
    
    process.stdin.on('end', () => {
        debugLog('[DEBUG] Bridge shutting down');
        process.exit(0);
    });
    
    // Handle process termination
    process.on('SIGINT', () => {
        debugLog('[DEBUG] Received SIGINT, shutting down');
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