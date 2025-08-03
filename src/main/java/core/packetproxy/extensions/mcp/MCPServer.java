package packetproxy.extensions.mcp;

import static packetproxy.util.Logging.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.function.Consumer;
import packetproxy.extensions.mcp.tools.ToolRegistry;

public class MCPServer {

	private final Gson gson;
	private final ToolRegistry toolRegistry;
	private final Consumer<String> logger;
	private boolean running = false;
	private BufferedReader reader;
	private PrintWriter writer;

	public MCPServer(Consumer<String> logger) {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.toolRegistry = new ToolRegistry();
		this.logger = logger;
		this.reader = new BufferedReader(new InputStreamReader(System.in));
		this.writer = new PrintWriter(System.out, true);
	}

	public void run() throws IOException {
		running = true;
		logger.accept("MCP Server listening on stdin/stdout");

		while (running) {
			try {
				String line = reader.readLine();
				if (line == null) {
					break; // EOF
				}

				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

				logger.accept("Received: " + line);
				processRequest(line);

			} catch (Exception e) {
				logger.accept("Error processing request: " + e.getMessage());
				log("MCP Server error: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public void stop() {
		running = false;
		try {
			if (reader != null) {
				reader.close();
			}
			if (writer != null) {
				writer.close();
			}
		} catch (IOException e) {
			logger.accept("Error closing streams: " + e.getMessage());
		}
	}

	public JsonObject processTestRequest(JsonObject request) throws Exception {
		String method = request.get("method").getAsString();
		JsonElement id = request.get("id");
		JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();

		JsonObject response = new JsonObject();
		response.addProperty("jsonrpc", "2.0");
		if (id != null) {
			response.add("id", id);
		}

		try {
			JsonObject result = handleMethod(method, params);
			response.add("result", result);
		} catch (Exception e) {
			JsonObject error = new JsonObject();
			error.addProperty("code", -32603);
			error.addProperty("message", "Internal error: " + e.getMessage());
			response.add("error", error);
			throw e;
		}

		return response;
	}

	private void processRequest(String requestLine) {
		try {
			JsonObject request = JsonParser.parseString(requestLine).getAsJsonObject();

			String method = request.get("method").getAsString();
			JsonElement id = request.get("id");
			JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();

			JsonObject response = new JsonObject();
			response.addProperty("jsonrpc", "2.0");
			if (id != null) {
				response.add("id", id);
			}

			try {
				JsonObject result = handleMethod(method, params);
				response.add("result", result);
			} catch (Exception e) {
				JsonObject error = new JsonObject();
				error.addProperty("code", -32603);
				error.addProperty("message", "Internal error: " + e.getMessage());
				response.add("error", error);
				logger.accept("Method error: " + e.getMessage());
			}

			String responseString = gson.toJson(response);
			writer.println(responseString);
			logger.accept("Sent: " + responseString);

		} catch (Exception e) {
			// Invalid JSON request
			JsonObject errorResponse = new JsonObject();
			errorResponse.addProperty("jsonrpc", "2.0");
			
			// Try to extract ID from the malformed request if possible
			JsonElement requestId = null;
			try {
				JsonObject partialRequest = JsonParser.parseString(requestLine).getAsJsonObject();
				if (partialRequest.has("id")) {
					requestId = partialRequest.get("id");
				}
			} catch (Exception parseError) {
				// If we can't parse at all, use null ID
			}
			errorResponse.add("id", requestId);

			JsonObject error = new JsonObject();
			error.addProperty("code", -32700);
			error.addProperty("message", "Parse error");
			errorResponse.add("error", error);

			writer.println(gson.toJson(errorResponse));
			logger.accept("Parse error: " + e.getMessage());
		}
	}

	private JsonObject handleMethod(String method, JsonObject params) throws Exception {
		switch (method) {
			case "initialize" :
				return handleInitialize(params);
			case "tools/list" :
				return handleToolsList();
			case "tools/call" :
				return handleToolsCall(params);
			case "resources/list" :
				return handleResourcesList();
			case "prompts/list" :
				return handlePromptsList();
			default :
				throw new Exception("Unknown method: " + method);
		}
	}

	private JsonObject handleInitialize(JsonObject params) {
		JsonObject result = new JsonObject();

		JsonObject capabilities = new JsonObject();
		JsonObject tools = new JsonObject();
		tools.addProperty("listChanged", true);
		capabilities.add("tools", tools);

		JsonObject serverInfo = new JsonObject();
		serverInfo.addProperty("name", "PacketProxy MCP Server");
		serverInfo.addProperty("version", "1.0.0");

		result.add("capabilities", capabilities);
		result.addProperty("protocolVersion", "2024-11-05");
		result.add("serverInfo", serverInfo);

		logger.accept("Client initialized");
		return result;
	}

	private JsonObject handleToolsList() {
		JsonObject result = new JsonObject();
		result.add("tools", toolRegistry.getToolsList());
		return result;
	}

	private JsonObject handleToolsCall(JsonObject params) throws Exception {
		if (!params.has("name")) {
			throw new Exception("Tool name is required");
		}

		String toolName = params.get("name").getAsString();
		JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

		return toolRegistry.callTool(toolName, arguments);
	}

	private JsonObject handleResourcesList() {
		JsonObject result = new JsonObject();
		JsonObject[] resources = {};
		result.add("resources", gson.toJsonTree(resources));
		return result;
	}

	private JsonObject handlePromptsList() {
		JsonObject result = new JsonObject();
		JsonObject[] prompts = {};
		result.add("prompts", gson.toJsonTree(prompts));
		return result;
	}
}
